import logging
import re
import math
from datetime import datetime, timezone, timedelta
from uuid import UUID
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, Query, Request, UploadFile, File, Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc, func, and_, or_, delete, update, case
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import selectinload
from app.core.database import get_db, get_redis
from app.core.timezone import utc_now, format_utc_iso
from app.models.post import Post, Category, PostEdit, PostImage, Like, Collection, PostSeries, SeriesItem
from app.models.poll import Poll, PollOption, PollVote
from app.models.user import User, Follow
from app.models.system import Notification
from app.schemas.interaction import PollDetailResponse, PollOptionResponse
from app.schemas.post import (
    PostCreateRequest,
    PostUpdateRequest,
    PostSimpleResponse,
    PostDetailResponse,
    TOCItem,
    PostDraftRequest,
    PostMoveCategoryRequest,
    BatchStatusRequest,
    PostSeriesCreateRequest,
    PostSeriesItemAddRequest,
    PostSeriesResponse,
    SeriesItemResponse,
)
from app.routers.deps import (
    get_current_root_admin,
    get_current_user,
    get_current_user_optional,
    get_current_admin,
    get_current_super_admin,
    get_current_moderator,
    is_admin_role,
)
from app.services.sensitive_service import sensitive_filter
from app.services.level_service import (
    add_user_experience,
    deduct_user_experience,
    record_user_activity,
    get_system_setting_int,
)
from app.services.image_service import save_upload_image
from app.utils.sanitizer import clean_html
from app.core.cache import get_cached_json, set_cached_json, invalidate_posts_cache, invalidate_categories_cache

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/posts", tags=["帖子与创作"])

# 普通用户对同一篇帖子的换区次数上限；管理员 / 版主不受此限制
CATEGORY_MOVE_LIMIT = 5

def extract_toc_and_reading_time(md_content: str):
    """从 Markdown 提取大纲目录与预估阅读时间"""
    toc: List[TOCItem] = []
    lines = md_content.split("\n")
    for line in lines:
        match = re.match(r'^(#{1,3})\s+(.+)$', line.strip())
        if match:
            level = len(match.group(1))
            text = match.group(2).strip()
            anchor_id = re.sub(r'[^\w\u4e00-\u9fa5]+', '-', text).strip('-').lower()
            toc.append(TOCItem(id=anchor_id, text=text, level=level))

    # 计算预计阅读时长 (中文约 400 字/分)
    chinese_chars = len(re.findall(r'[\u4e00-\u9fa5]', md_content))
    words = len(re.findall(r'\b\w+\b', md_content))
    total_effective = chinese_chars + words
    minutes = max(1, math.ceil(total_effective / 350))

    return toc, minutes

def smart_extract_title_from_md(content_md: str, post_type: str = "post", created_at: Optional[datetime] = None) -> Optional[str]:
    """
    智能从真实正文 Markdown 中提取真实标题：
    1. 扫描加粗核心字段：- **重点技术进展**：xxx
    2. 扫描 Markdown 标题：#### 1. 核心聚焦：xxx 或 ### xxx
    3. 扫描首个有实际内容的自然文本段
    4. 如果是早报类型，组合日期前缀 【极客早报 · MM-DD】
    """
    if not content_md or not content_md.strip():
        return None

    lines = content_md.strip().split("\n")
    extracted_topic = None

    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith(">") or line.startswith("---") or line.startswith("```"):
            continue

        # 匹配加粗核心字段：- **重点技术进展**：xxx
        bold_match = re.match(r'^[-*]?\s*\*\*([^*]+)\*\*[:：]\s*(.+)$', line)
        if bold_match:
            val = re.sub(r'[*_`#]', '', bold_match.group(2)).strip()
            # 兼容中英文混合及纯英文较短标题（>=3有效字符），并过滤默认模板词
            if len(val) >= 3 and not any(p in val for p in ["在此输入", "一句话描述", "https://"]):
                extracted_topic = val
                break

        # 匹配 Markdown 标题行：#### 1. 核心聚焦：xxx 或 ### xxx
        h_match = re.match(r'^#{1,6}\s+(.+)$', line)
        if h_match:
            heading_text = re.sub(r'^[🌅🚀🔥💡📌\s]+', '', h_match.group(1)).strip()
            colon_match = re.search(r'[:：]\s*(.+)$', heading_text)
            if colon_match:
                after_colon = colon_match[1].strip()
                if len(after_colon) >= 3 and not any(p in after_colon for p in ["在此输入", "今日技术头条"]):
                    extracted_topic = after_colon
                    break

            generic_words = ["今日极客要闻", "核心架构聚焦", "开源前沿与硬核工具推荐", "极客思考与今日互动", "今日技术头条与架构前沿精选"]
            if len(heading_text) >= 3 and not any(w in heading_text for w in generic_words):
                extracted_topic = re.sub(r'^\d+[\.、\s]*', '', heading_text).strip()
                break

    if not extracted_topic:
        for raw_line in lines:
            line = raw_line.strip()
            if not line or line.startswith("#") or line.startswith(">") or line.startswith("-") or line.startswith("*"):
                continue
            clean = re.sub(r'[*_`#\[\]]', '', line).strip()
            if len(clean) >= 3 and "每日聚合全球尖端算力" not in clean:
                first_sent = re.split(r'[。！？\n]', clean)[0]
                extracted_topic = (first_sent if len(first_sent) >= 3 else clean)[:45]
                break

    if not extracted_topic:
        return None

    if post_type == "news":
        date_str = ""
        if created_at:
            date_str = created_at.strftime("%m-%d")
        else:
            date_str = datetime.now(timezone.utc).strftime("%m-%d")
        return f"【极客早报 · {date_str}】{extracted_topic}"

async def build_poll_detail_response(poll: Optional[Poll], user: Optional[User], db: AsyncSession) -> Optional[PollDetailResponse]:
    if not poll:
        return None
    # 显式异步载入全部选项，避免 relationship lazyload MissingGreenlet 错误
    opt_q = select(PollOption).where(PollOption.poll_id == poll.id).order_by(PollOption.id.asc())
    opt_res = await db.execute(opt_q)
    options = opt_res.scalars().all()
    now = datetime.now(timezone.utc)
    exp = poll.expires_at if (poll.expires_at and poll.expires_at.tzinfo) else (poll.expires_at.replace(tzinfo=timezone.utc) if poll.expires_at else now)
    is_expired = exp < now if exp else False

    user_voted_options = set()
    has_voted = False
    if user:
        v_q = select(PollVote.option_id).where(PollVote.poll_id == poll.id, PollVote.user_id == user.id)
        v_res = await db.execute(v_q)
        user_voted_options = set(v_res.scalars().all())
        has_voted = len(user_voted_options) > 0

    total_votes = sum(o.vote_count for o in options)
    options_res = []
    for o in options:
        pct = round((o.vote_count / total_votes * 100), 1) if total_votes > 0 else 0.0
        options_res.append(PollOptionResponse(
            id=o.id,
            option_text=o.option_text,
            vote_count=o.vote_count,
            percentage=pct,
            is_voted=(o.id in user_voted_options)
        ))

    return PollDetailResponse(
        id=poll.id,
        title=poll.title,
        is_multiple=poll.is_multiple,
        max_choices=poll.max_choices,
        expires_at=poll.expires_at,
        is_expired=is_expired,
        total_votes=total_votes,
        has_voted=has_voted,
        options=options_res
    )



def _extract_clean_summary(md: Optional[str], max_len: int = 140) -> Optional[str]:
    if not md:
        return None
    import re as _re
    text = _re.sub(r'!\[.*?\]\(.*?\)', '', md)
    text = _re.sub(r'\[(.*?)\]\(.*?\)', r'\1', text)
    text = _re.sub(r'`[\s\S]*?`', '', text)
    text = _re.sub(r'[^]+', '', text)
    text = _re.sub(r'#{1,6}\s+', '', text)
    text = _re.sub(r'[*_~>|]', '', text)
    text = _re.sub(r'\s+', ' ', text).strip()
    if not text:
        return None
    return (text[:max_len] + '...') if len(text) > max_len else text

def _first_content_image(md: str) -> Optional[str]:
    """取正文里的第一张插图，作为未设置封面时的列表展示图。

    只匹配 markdown 图片语法，跳过外链统计像素之类的非内容图。
    """
    if not md:
        return None
    import re as _re
    m = _re.search(r"!\[[^\]]*\]\(\s*([^)\s]+)", md)
    if not m:
        return None
    url = m.group(1).strip()
    if not url or url.startswith("data:"):
        return None
    return url




async def _check_album_weekly_limit(db, user, exclude_post_id=None) -> None:
    """九宫格相册每人每周只能发一次（考虑服务器存储压力）。

    按东八区自然周（周一 00:00 起）计算；超级管理员不受限，便于站点运营。
    """
    if getattr(user, "role", "") == "super_admin":
        return
    from datetime import timedelta, timezone as _tz

    now = datetime.now(_tz.utc)
    cst = now.astimezone(_tz(timedelta(hours=8)))
    week_start_cst = (cst - timedelta(days=cst.weekday())).replace(
        hour=0, minute=0, second=0, microsecond=0
    )
    week_start = week_start_cst.astimezone(_tz.utc)

    q = select(func.count(Post.id)).where(
        Post.author_id == user.id,
        Post.image_urls.isnot(None),
        Post.created_at >= week_start,
    )
    if exclude_post_id is not None:
        q = q.where(Post.id != exclude_post_id)
    cnt = (await db.execute(q)).scalar() or 0
    if cnt >= 1:
        raise HTTPException(
            status_code=400,
            detail="九宫格相册每周只能分享一次，下周再来吧（正文里仍可单独插图）",
        )


@router.get("/categories")
async def list_categories(db: AsyncSession = Depends(get_db)):
    cached_cats = await get_cached_json("cache:categories:all")
    if cached_cats is not None:
        return cached_cats

    query = select(Category).order_by(Category.sort_order.asc(), Category.id.asc())
    res = await db.execute(query)
    cats = list(res.scalars().all())
    # 确保综合交流板块核心分类存在
    has_general = any(c.slug == "general" or c.name == "综合交流" for c in cats)
    if not has_general:
        gen = Category(name="综合交流", slug="general", description="极客茶水间与闲聊讨论", sort_order=6)
        db.add(gen)
        try:
            await db.commit()
            await db.refresh(gen)
            cats.append(gen)
        except Exception:
            await db.rollback()
    result = [{"id": c.id, "name": c.name, "slug": c.slug, "description": c.description} for c in cats]
    await set_cached_json("cache:categories:all", result, ttl=3600)
    return result

def assert_post_interactable(post: Post, user: User):
    """点赞/收藏/评论等互动只允许发生在公开文章上。

    草稿、定时未发布、已下架文章对其他人等同于不存在，避免通过 ID 探测未公开内容。
    """
    if post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在")
    if post.status != "published" and post.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=404, detail="文章不存在")


async def mark_images_attached(
    db: AsyncSession,
    post_id: UUID,
    content: str,
    extra_urls: Optional[List[str]] = None,
):
    """提取文章或草稿正文/封面中的本地或S3图片URL，将其 PostImage 状态升级为 attached，
    防止定时清理任务将其视作孤儿临时图片删除导致 404。

    `extra_urls` 用于九宫格相册：相册图片不在正文 Markdown 里，
    不一起标记的话除第一张（同时作为封面）外都会被当作孤儿图物理删除。
    """
    if not content and not extra_urls:
        return
    matches = set(re.findall(r'(?:https?://[^\s\)\"\'><]+)?/uploads/[^\s\)\"\'><]+', content or ""))
    for u in (extra_urls or []):
        if not u:
            continue
        u = str(u).strip()
        idx = u.find("/uploads/")
        if idx != -1:
            matches.add(u[idx:])
        elif u.startswith("/uploads/"):
            matches.add(u)
    if not matches:
        return
    conditions = []
    for u in matches:
        conditions.append(PostImage.storage_path == u)
        if u.startswith("http://") or u.startswith("https://"):
            idx = u.find("/uploads/")
            if idx != -1:
                rel = u[idx:]
                conditions.append(PostImage.storage_path == rel)
                conditions.append(PostImage.storage_path.endswith(rel))
        else:
            conditions.append(PostImage.storage_path.endswith(u))
    await db.execute(
        update(PostImage)
        .where(or_(*conditions))
        .values(status="attached", post_id=post_id)
    )


async def maybe_unlock_word_tribulation(db, user, post) -> None:
    """彩蛋「字数渡劫人」。

    字数口径与编辑器显示的「N 字符」完全一致：直接用 content_md 的原始长度，
    不去空白、不解析 Markdown。作者看到 666 就是 666。
    仅在「已发布」文章上判定，草稿/定时/编辑历史都不占用当天额度。
    """
    if getattr(post, "status", None) != "published":
        return
    try:
        if len(getattr(post, "content_md", "") or "") in (666, 888, 999):
            from app.services.achievement_engine import unlock_egg
            result = await unlock_egg(db, user, "egg_word_tribulation")
            if not result.get("unlocked"):
                logger.info(
                    "word_tribulation skipped: post=%s reason=%s",
                    getattr(post, "id", "?"), result.get("reason"),
                )
    except Exception:
        # 不能再静默吞掉：否则「条件命中但不解锁」完全无法排查
        logger.exception("word_tribulation check failed: post=%s", getattr(post, "id", "?"))


@router.post("", response_model=PostDetailResponse)
async def create_post(
    req: PostCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # 见习会员拦截：见习期用户需累积活跃 5 天转正后解锁发帖权限
    if user.status == "probation":
        raise HTTPException(
            status_code=403,
            detail="当前账号处于见习观察期（累计活跃 5 天后自动转正），见习期间暂未开放公开发布文章特权，欢迎在社区积极互动学习～"
        )

    # 发布状态解析：draft(草稿) / scheduled(定时) / published(立即发布)
    requested_status = (req.status or "published").lower()
    if requested_status not in ("draft", "scheduled", "published"):
        raise HTTPException(status_code=400, detail="不支持的发布状态，仅支持 draft / scheduled / published")

    scheduled_at_value = None
    if requested_status == "scheduled":
        # 定时发布属于运营能力，仅管理员及以上可用
        if user.role not in ("super_admin", "admin"):
            raise HTTPException(status_code=403, detail="定时发布为管理员专属能力，普通用户请直接发布或保存草稿")
        if not req.scheduled_at:
            raise HTTPException(status_code=400, detail="定时发布必须指定发布时间")
        sched = req.scheduled_at if req.scheduled_at.tzinfo else req.scheduled_at.replace(tzinfo=timezone.utc)
        if sched <= datetime.now(timezone.utc):
            raise HTTPException(status_code=400, detail="定时发布时间必须晚于当前时间")
        scheduled_at_value = sched

    # 频率防护：仅针对「公开发布」，草稿与定时保存不受 20 秒冷却限制
    if requested_status == "published" and user.role not in ["super_admin", "admin"]:
        latest_post_res = await db.execute(
            select(Post.created_at)
            .where(Post.author_id == user.id, Post.status == "published")
            .order_by(Post.created_at.desc())
            .limit(1)
        )
        latest_created_at = latest_post_res.scalar_one_or_none()
        if latest_created_at:
            now_utc = datetime.now(timezone.utc)
            latest_dt = latest_created_at if latest_created_at.tzinfo else latest_created_at.replace(tzinfo=timezone.utc)
            diff_seconds = (now_utc - latest_dt).total_seconds()
            if diff_seconds < 20:
                raise HTTPException(
                    status_code=429,
                    detail=f"发帖过于频繁，防刷保护已生效，请等待 {math.ceil(20 - diff_seconds)} 秒后再发布新文章"
                )

    # 官方新闻早报/每日日报只有管理员与超级管理员可发，普通用户自动降级为普通帖子
    effective_post_type = req.post_type
    if effective_post_type == "news" and user.role not in ["super_admin", "admin"]:
        effective_post_type = "post"

    # 标题处理：优先使用用户填写的真实标题；用户未填写则直接从真实正文中智能提取！
    raw_title = req.title.strip() if req.title and req.title.strip() else ""
    if not raw_title or "今日技术头条与架构前沿精选" in raw_title:
        extracted = smart_extract_title_from_md(req.content_md, effective_post_type)
        if extracted:
            raw_title = extracted
        elif not raw_title:
            raw_title = "极客技术前沿讨论"

    # 敏感词审查
    is_blocked, hit_words, replaced_title = sensitive_filter.scan(raw_title)
    if is_blocked:
        raise HTTPException(status_code=400, detail=f"标题包含违规内容: {', '.join(hit_words)}")

    is_blocked_c, hit_words_c, replaced_content = sensitive_filter.scan(req.content_md)
    if is_blocked_c:
        raise HTTPException(status_code=400, detail=f"正文包含违规内容: {', '.join(hit_words_c)}")

    toc, read_time = extract_toc_and_reading_time(replaced_content)

    # 好文分享：必须声明原创/转载；转载必须注明来源，否则不允许发布
    try:
        from app.models.post import Category as _Cat
        _cat = (await db.execute(
            select(_Cat).where(_Cat.id == req.category_id)
        )).scalar_one_or_none() if req.category_id else None
        if _cat and _cat.slug == "share":
            _st = (getattr(req, "source_type", None) or "").strip()
            _note = (getattr(req, "source_note", None) or "").strip()
            if _st not in ("original", "repost"):
                raise HTTPException(status_code=400, detail="好文分享需选择「原创」或「转载」")
            if _st == "repost" and len(_note) < 2:
                raise HTTPException(status_code=400, detail="转载必须注明文章来源")
            if req.status == "published" and not getattr(req, "source_ack", False):
                raise HTTPException(status_code=400, detail="请先勾选「我已知晓」承诺书")
    except HTTPException:
        raise
    except Exception:
        pass

    # 验证分类是否存在，若不存在自动降级到首选默认分类
    valid_category_id = None
    category_name = "综合交流"
    if req.category_id:
        cat_res = await db.execute(select(Category).where(Category.id == req.category_id))
        cat_obj = cat_res.scalar_one_or_none()
        if cat_obj:
            valid_category_id = cat_obj.id
            category_name = cat_obj.name
        else:
            gen_res = await db.execute(select(Category).where(or_(Category.slug == "general", Category.name == "综合交流")))
            gen_cat = gen_res.scalar_one_or_none()
            if gen_cat:
                valid_category_id = gen_cat.id
                category_name = gen_cat.name
            else:
                first_cat_res = await db.execute(select(Category).order_by(Category.sort_order.asc(), Category.id.asc()).limit(1))
                first_cat = first_cat_res.scalar_one_or_none()
                if first_cat:
                    valid_category_id = first_cat.id
                    category_name = first_cat.name
    else:
        first_cat_res = await db.execute(select(Category).order_by(Category.sort_order.asc(), Category.id.asc()).limit(1))
        first_cat = first_cat_res.scalar_one_or_none()
        if first_cat:
            valid_category_id = first_cat.id
            category_name = first_cat.name

    # 限制每日文章发布上限 (超级管理员与管理员免限制)
    beijing_tz = timezone(timedelta(hours=8))
    now_bj = datetime.now(beijing_tz)
    start_of_day_bj = datetime(now_bj.year, now_bj.month, now_bj.day, 0, 0, 0, tzinfo=beijing_tz)
    start_of_day_utc = start_of_day_bj.astimezone(timezone.utc)

    if requested_status == "published" and not is_admin_role(user.role):
        daily_post_limit = await get_system_setting_int(db, "daily_post_limit", 5)
        today_posts_cnt_res = await db.execute(
            select(func.count(Post.id)).where(
                Post.author_id == user.id,
                Post.status == "published",
                Post.created_at >= start_of_day_utc
            )
        )
        today_posts_cnt = today_posts_cnt_res.scalar() or 0
        if today_posts_cnt >= daily_post_limit:
            raise HTTPException(
                status_code=400,
                detail=f"您今日发帖数量已达上限（每日限发 {daily_post_limit} 篇），请明天再来交流～"
            )

    clean_tags = ",".join([t.strip().lstrip('#') for t in req.tags if t.strip()]) if req.tags else ""

    if req.image_urls:
        await _check_album_weekly_limit(db, user)

    new_post = Post(
        author_id=user.id,
        category_id=valid_category_id,
        title=replaced_title,
        content_md=replaced_content,
        content_html=clean_html(replaced_content),
        source_type=(getattr(req, "source_type", None) or None),
        source_note=((getattr(req, "source_note", None) or "").strip()[:500] or None),
        content_type=getattr(req, "content_type", "markdown") or "markdown",
        post_type=effective_post_type,
        comment_policy=req.comment_policy,
        tags=clean_tags,
        status=requested_status,
        scheduled_at=scheduled_at_value,
        image_urls=(req.image_urls or None),
        cover_image=(req.image_urls[0] if req.image_urls else req.cover_image),
    )
    if requested_status == "published":
        new_post.first_published_at = utc_now()
    db.add(new_post)
    await db.flush()
    await mark_images_attached(
        db, new_post.id,
        f"{req.content_md or ''} {req.cover_image or ''}",
        extra_urls=req.image_urls,
    )

    # 发帖经验奖励：正文有效字符 >= 15 字，受系统每日经验获取上限保护
    clean_content_text = re.sub(r'[\s#*`\-_~>|\[\]\(\)!]+', '', req.content_md or "")
    is_substantive = len(clean_content_text) >= 15

    # 只有真正公开发布才计入发帖经验，避免"存草稿刷经验"
    if is_substantive and requested_status == "published":
        post_exp_reward = await get_system_setting_int(db, "post_exp_reward", 15)
        await add_user_experience(user, post_exp_reward, db)
    await record_user_activity(user, db)

    # 发起投票 (若附带投票)
    poll_id = None
    created_poll = None
    poll_req = getattr(req, "poll", None)
    if poll_req and getattr(poll_req, "options", None) and len(poll_req.options) >= 2:
        duration = getattr(poll_req, "duration_days", 7) or 7
        new_poll = Poll(
            post_id=new_post.id,
            title=poll_req.title,
            is_multiple=poll_req.is_multiple,
            max_choices=poll_req.max_choices,
            expires_at=datetime.now(timezone.utc) + timedelta(days=duration)
        )
        db.add(new_poll)
        await db.flush()
        for opt_text in poll_req.options:
            if opt_text and str(opt_text).strip():
                poll_opt = PollOption(poll_id=new_poll.id, option_text=str(opt_text).strip())
                db.add(poll_opt)
        poll_id = new_poll.id
        created_poll = new_poll

    # 注意：草稿由用户在草稿箱中自主管理，发帖不再暴力清空该用户的全部历史草稿

    # 问答广场：沿用同一套分区发帖逻辑，命中 qa 分区时额外锁定 10 经验悬赏
    if requested_status == "published" and req.category_id:
        from app.models.post import Category as _QCat
        _qcat = (await db.execute(
            select(_QCat).where(_QCat.id == req.category_id)
        )).scalar_one_or_none()
        if _qcat and _qcat.slug == "qa":
            from app.services.qa_service import create_qa_question
            await db.flush()
            await create_qa_question(db, new_post, user)

    await db.commit()
    await db.refresh(new_post)
    await invalidate_posts_cache()

    # 新帖发布后立即触发一条 AI 评论（新闻早报类由 auto_comment_post 内部过滤）
    if requested_status == "published" and effective_post_type != "news":
        from app.services.ai_service import schedule_ai_comment
        schedule_ai_comment(str(new_post.id))

    await maybe_unlock_word_tribulation(db, user, new_post)

    poll_detail = await build_poll_detail_response(created_poll, user, db) if created_poll else None

    return PostDetailResponse(
        id=new_post.id,
        title=new_post.title,
        content_md=new_post.content_md,
        content_html=new_post.content_html,
        content_type=getattr(new_post, "content_type", "markdown") or "markdown",
        source_type=getattr(new_post, "source_type", None),
        source_note=getattr(new_post, "source_note", None),
        post_type=new_post.post_type,
        status=new_post.status,
        comment_policy=new_post.comment_policy,
        is_pinned=new_post.is_pinned,
        view_count=new_post.view_count,
        like_count=new_post.like_count,
        comment_count=new_post.comment_count,
        cover_image=new_post.cover_image,
        image_urls=getattr(new_post, "image_urls", None),
        created_at=new_post.created_at,
        reading_time_minutes=read_time,
        category_id=new_post.category_id,
        category_name=category_name,
        author_id=user.id,
        author_username=user.username,
        author_nickname=user.nickname,
        author_avatar=user.avatar_url,
        author_level=user.level,
        tags=[t for t in clean_tags.split(",") if t] if clean_tags else [],
        toc=toc,
        poll_id=poll_id,
        poll=poll_detail
    )

def _build_match_snippet(content_md: str, query: str, length: int = 120) -> Optional[str]:
    """搜索结果摘要：截取关键词附近的纯文本片段，说明"为什么命中"。"""
    plain = re.sub(r"[#*`>~\-\[\]\(\)!|]+", " ", content_md or "")
    plain = " ".join(plain.split())
    keyword = (query or "").strip()
    if not plain or not keyword:
        return None

    lower = plain.lower()
    pos = -1
    for token in keyword.split()[:5]:
        pos = lower.find(token.lower())
        if pos >= 0:
            break
    if pos < 0:
        return plain[:length] + ("…" if len(plain) > length else "")

    start = max(0, pos - 30)
    end = min(len(plain), start + length)
    return ("…" if start > 0 else "") + plain[start:end] + ("…" if end < len(plain) else "")


@router.get("", response_model=List[PostSimpleResponse])
async def list_posts(
    response: Response,
    q: Optional[str] = None,
    category_id: Optional[int] = None,
    category: Optional[str] = None,
    post_type: Optional[str] = None,
    tag: Optional[str] = None,
    author: Optional[str] = None,
    time_range: Optional[str] = Query(None, pattern="^(week|month|year)$"),
    sort_by: str = Query("latest", pattern="^(latest|hot|comments|relevance)$"),
    page: int = Query(1, ge=1),
    size: int = Query(50, ge=1, le=200),
    db: AsyncSession = Depends(get_db)
):
    # 公共过滤条件：列表查询与总数统计共用，保证「共 N 篇」与分页一致
    where_clauses = [
        Post.status == "published",
        User.status.not_in(("banned", "deactivated", "deleting")),
    ]

    if q and q.strip():
        tokens = [t.strip() for t in q.strip().split() if t.strip()][:10]
        token_clauses = []
        for t in tokens:
            # 转义 ILIKE 通配符，避免 %/_ 构造全表代价查询
            escaped = t.replace('\\', '\\\\').replace('%', '\\%').replace('_', '\\_')
            ct = f"%{escaped}%"
            token_clauses.append(
                or_(
                    Post.title.ilike(ct, escape='\\'),
                    Post.tags.ilike(ct, escape='\\'),
                    Post.content_md.ilike(ct, escape='\\')
                )
            )
        if token_clauses:
            where_clauses.append(and_(*token_clauses))

    if category_id:
        where_clauses.append(Post.category_id == category_id)
    if category and category != "全部":
        where_clauses.append(Post.category.has(Category.name == category))
    if post_type:
        where_clauses.append(Post.post_type == post_type)
    if tag:
        clean_t = tag.strip().lstrip('#').replace('\\', '\\\\').replace('%', '\\%').replace('_', '\\_')
        where_clauses.append(Post.tags.like(f"%{clean_t}%", escape='\\'))
    if author:
        where_clauses.append(User.username == author.strip())
    if time_range:
        days = {"week": 7, "month": 30, "year": 365}[time_range]
        where_clauses.append(Post.created_at >= datetime.now(timezone.utc) - timedelta(days=days))

    base_query = (
        select(Post)
        .join(User, Post.author_id == User.id)
        .where(*where_clauses)
    )

    # 首页第一页（无关键词、无作者/时间筛选）优先读 Redis 缓存；
    # 缓存同时保存列表与总数，命中时无需再执行一次 COUNT。
    cache_key = None
    if page == 1 and not q and not author and not time_range:
        # 注意：category（分类名）与 size 都必须进入缓存键。
        # size 不参与键控时，先到的 10 条请求会把缓存写死，后到的 size=100 也只能拿到 10 条，
        # 前端据此判定「没有更多」→ 无限滚动直接卡死。
        cache_key = (
            f"cache:posts:cat_{category_id or 0}_{category or 'all'}"
            f"_type_{post_type or 'all'}_tag_{tag or 'none'}_sort_{sort_by}_size_{size}"
        )
        cached = await get_cached_json(cache_key)
        if isinstance(cached, dict) and isinstance(cached.get("items"), list):
            response.headers["X-Total-Count"] = str(int(cached.get("total") or len(cached["items"])))
            response.headers["X-Page"] = str(page)
            response.headers["X-Page-Size"] = str(size)
            return cached["items"]
        if isinstance(cached, list) and cached:
            # 兼容旧格式缓存（只有列表、没有总数），30 秒 TTL 后自然淘汰；
            # 仍按本次 size 截断，避免跨 size 复用导致条数错乱
            return cached[:size]

    # 总数：用同一组条件统计，前端据此渲染「共 N 篇」与分页
    total = (await db.execute(
        select(func.count(Post.id))
        .select_from(Post)
        .join(User, Post.author_id == User.id)
        .where(*where_clauses)
    )).scalar_one()
    response.headers["X-Total-Count"] = str(int(total or 0))
    response.headers["X-Page"] = str(page)
    response.headers["X-Page-Size"] = str(size)

    query = base_query.options(
        selectinload(Post.author), selectinload(Post.category), selectinload(Post.poll)
    )

    if sort_by == "hot":
        # 工业级 Hacker News / Reddit 热度衰减算法（SQL 全量排序，修复此前 150 条
        # 候选截断导致深分页返回空页的问题）:
        # Score = (点赞*2 + 评论*3 + 浏览*0.2 + 收藏*3) / (距离发布小时数 + 2)^1.5
        hours_elapsed = func.greatest(
            0.0,
            func.extract("epoch", func.now() - Post.created_at) / 3600.0
        )
        decay_score = (
            (Post.like_count * 2.0) + (Post.comment_count * 3.0)
            + (Post.view_count * 0.2) + (func.coalesce(Post.collect_count, 0) * 3.0)
        ) / func.power(hours_elapsed + 2.0, 1.5)
        query = query.order_by(Post.is_pinned.desc(), decay_score.desc())
        query = query.offset((page - 1) * size).limit(size)
        res = await db.execute(query)
        posts = res.scalars().all()
    elif sort_by == "comments":
        # 「热议」= 评论数优先，而不是与「热门互动」共用同一套热度公式
        query = query.order_by(Post.is_pinned.desc(), Post.comment_count.desc(), Post.created_at.desc())
        query = query.offset((page - 1) * size).limit(size)
        res = await db.execute(query)
        posts = res.scalars().all()
    elif sort_by == "relevance" and q and q.strip():
        # 综合匹配：标题命中 > 标签命中 > 互动量 > 时间
        first_token = q.strip().split()[0][:50]
        escaped_token = first_token.replace('\\', '\\\\').replace('%', '\\%').replace('_', '\\_')
        like_token = f"%{escaped_token}%"
        title_hit = case((Post.title.ilike(like_token, escape='\\'), 0), else_=1)
        tag_hit = case((Post.tags.ilike(like_token, escape='\\'), 0), else_=1)
        query = query.order_by(
            Post.is_pinned.desc(), title_hit, tag_hit, Post.like_count.desc(), Post.created_at.desc()
        )
        query = query.offset((page - 1) * size).limit(size)
        res = await db.execute(query)
        posts = res.scalars().all()
    else:
        query = query.order_by(Post.is_pinned.desc(), Post.created_at.desc())
        query = query.offset((page - 1) * size).limit(size)
        res = await db.execute(query)
        posts = res.scalars().all()

    result = []
    for p in posts:
        _, r_time = extract_toc_and_reading_time(p.content_md)
        actual_title = p.title
        if not actual_title or "今日技术头条与架构前沿精选" in actual_title:
            extracted = smart_extract_title_from_md(p.content_md, p.post_type, p.created_at)
            if extracted:
                actual_title = extracted

        result.append(PostSimpleResponse(
            id=p.id,
            title=actual_title,
            post_type=p.post_type,
            status=p.status,
            is_pinned=p.is_pinned,
            is_essence=bool(getattr(p, 'is_essence', False)),
            view_count=p.view_count,
            like_count=p.like_count,
            comment_count=p.comment_count,
            cover_image=p.cover_image,
            image_urls=getattr(p, 'image_urls', None),
            display_image=p.cover_image or _first_content_image(p.content_md),
            summary=_extract_clean_summary(p.content_md),
            created_at=p.created_at,
            reading_time_minutes=r_time,
            author_id=p.author_id,
            author_username=p.author.username if p.author else "用户已注销",
            author_nickname=p.author.nickname if p.author else None,
            author_avatar=p.author.avatar_url if p.author else "",
            author_level=p.author.level if p.author else 0,
            category_id=p.category_id,
            category_name=p.category.name if p.category else None,
            tags=[t for t in p.tags.split(",") if t] if p.tags else [],
            poll_id=p.poll.id if p.poll else None,
            match_snippet=_build_match_snippet(p.content_md, q) if q else None,
        ))

    if cache_key and result:
        cache_data = [p.model_dump(mode='json') for p in result]
        await set_cached_json(cache_key, {"items": cache_data, "total": int(total or 0)}, ttl=30)

    return result

@router.get("/latest-news", response_model=Optional[PostSimpleResponse])
async def get_latest_news(db: AsyncSession = Depends(get_db)):
    """首页置顶日报：永远返回「最新一期」已发布日报。

    不能用普通列表接口代替——列表会按 最新/热议/评论 排序、并受分类与时间筛选影响，
    结果就是切到「热议」时置顶卡片跳到几天前的旧日报，分类筛空时甚至会把普通文章当日报展示。
    这里固定按创建时间倒序取一条，与任何前端筛选状态无关。
    """
    row = (await db.execute(
        select(Post)
        .join(User, Post.author_id == User.id)
        .options(selectinload(Post.author), selectinload(Post.category), selectinload(Post.poll))
        .where(
            Post.status == "published",
            Post.post_type == "news",
            User.status.not_in(("banned", "deactivated", "deleting")),
        )
        .order_by(Post.created_at.desc())
        .limit(1)
    )).scalars().first()

    if not row:
        return None

    _, r_time = extract_toc_and_reading_time(row.content_md)
    actual_title = row.title
    if not actual_title or "今日技术头条与架构前沿精选" in actual_title:
        extracted = smart_extract_title_from_md(row.content_md, row.post_type, row.created_at)
        if extracted:
            actual_title = extracted

    return PostSimpleResponse(
        id=row.id,
        title=actual_title,
        post_type=row.post_type,
        status=row.status,
        is_pinned=row.is_pinned,
        is_essence=bool(getattr(row, 'is_essence', False)),
        view_count=row.view_count,
        like_count=row.like_count,
        comment_count=row.comment_count,
        cover_image=row.cover_image,
        image_urls=getattr(row, 'image_urls', None),
        display_image=row.cover_image or _first_content_image(row.content_md),
        summary=_extract_clean_summary(row.content_md),
        created_at=row.created_at,
        reading_time_minutes=r_time,
        author_id=row.author_id,
        author_username=row.author.username if row.author else "用户已注销",
        author_nickname=row.author.nickname if row.author else None,
        author_avatar=row.author.avatar_url if row.author else "",
        author_level=row.author.level if row.author else 0,
        category_id=row.category_id,
        category_name=row.category.name if row.category else None,
        tags=[t for t in row.tags.split(",") if t] if row.tags else [],
        poll_id=row.poll.id if row.poll else None,
    )


@router.get("/feed/following", response_model=List[PostSimpleResponse])
async def list_following_feed(
    page: int = Query(1, ge=1),
    # 前端关注流无分页入口，单次拉取 size=100；此处上限需 >= 100，否则会 422 导致列表恒为空
    size: int = Query(20, ge=1, le=200),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """当前登录用户所关注创作者的专属动态流"""
    fol_q = select(Follow.following_id).where(Follow.follower_id == current_user.id)
    fol_res = await db.execute(fol_q)
    following_ids = fol_res.scalars().all()
    if not following_ids:
        return []

    query = (
        select(Post)
        .options(selectinload(Post.author), selectinload(Post.category))
        .join(User, Post.author_id == User.id)
        .where(
            Post.status == "published",
            Post.author_id.in_(following_ids),
            User.status.not_in(("banned", "deactivated", "deleting"))  # 被关注者封禁/注销后其历史动态不再出现在关注流
        )
        .order_by(Post.created_at.desc())
        .offset((page - 1) * size)
        .limit(size)
    )
    res = await db.execute(query)
    posts = res.scalars().all()

    result = []
    for p in posts:
        _, r_time = extract_toc_and_reading_time(p.content_md)
        actual_title = p.title
        if not actual_title or "今日技术头条与架构前沿精选" in actual_title:
            extracted = smart_extract_title_from_md(p.content_md, p.post_type, p.created_at)
            if extracted:
                actual_title = extracted

        result.append(PostSimpleResponse(
            id=p.id,
            title=actual_title,
            post_type=p.post_type,
            status=p.status,
            is_pinned=p.is_pinned,
            is_essence=bool(getattr(p, 'is_essence', False)),
            view_count=p.view_count,
            like_count=p.like_count,
            comment_count=p.comment_count,
            cover_image=p.cover_image,
            image_urls=getattr(p, 'image_urls', None),
            display_image=p.cover_image or _first_content_image(p.content_md),
            summary=_extract_clean_summary(p.content_md),
            created_at=p.created_at,
            reading_time_minutes=r_time,
            author_username=p.author.username if p.author else "用户已注销",
            author_nickname=p.author.nickname if p.author else None,
            author_avatar=p.author.avatar_url if p.author else "",
            author_level=p.author.level if p.author else 0,
            category_name=p.category.name if p.category else None,
            tags=[t for t in p.tags.split(",") if t] if p.tags else []
        ))
    return result

def clean_post_title(title: str, content_md: str = "") -> str:
    if not title:
        return "技术前沿探索"
    clean = title.strip()
    if "今日技术头条与架构前沿精选" in clean:
        if content_md:
            import re
            for line in content_md.split("\n"):
                line = line.strip()
                if not line or line.startswith(">") or "今日极客要闻" in line:
                    continue
                bold_m = re.match(r"^[-*]?\s*\*\*([^*]+)\*\*[:：]\s*(.+)$", line)
                if bold_m:
                    val = bold_m.group(2).strip().replace("*", "").replace("#", "")
                    if len(val) >= 4 and "在此输入" not in val and "https://" not in val:
                        return f"【极客早报】{val[:42]}"
                h_m = re.match(r"^#{1,6}\s+(.+)$", line)
                if h_m and "今日极客要闻" not in h_m.group(1):
                    val = h_m.group(1).strip()
                    if ":" in val or "：" in val:
                        val = val.split(":", 1)[-1].split("：", 1)[-1].strip()
                    if len(val) >= 4 and "在此输入" not in val:
                        return f"【极客早报】{val[:42]}"
        return clean.replace("今日技术头条与架构前沿精选", "全球前沿科技精选与深度动态")
    return clean

async def get_24h_hot_posts(db: AsyncSession, limit: int = 5) -> list[Post]:
    """
    获取 24 小时热议精选：
    1. 优先严格筛选最近 24 小时内发表的已发布文章，按互动度 (评论*3 + 点赞*2 + 浏览*0.2) 降序；
    2. 若 24 小时内文章不足 limit 篇，向后适度平滑补充至最近 48 小时发表的文章；
    3. 严格杜绝超过 48 小时（如数天前的老文章）依然霸占24小时热议榜单的问题。
    """
    now = utc_now()
    since_24h = now - timedelta(hours=24)
    q_24h = (
        select(Post)
        .where(Post.status == "published", Post.created_at >= since_24h)
        .order_by((Post.comment_count * 3 + Post.like_count * 2 + Post.view_count * 0.2).desc())
        .limit(limit)
    )
    posts = list((await db.execute(q_24h)).scalars().all())

    if len(posts) < limit:
        needed = limit - len(posts)
        existing_ids = [p.id for p in posts]
        since_48h = now - timedelta(hours=48)
        q_fallback = (
            select(Post)
            .where(
                Post.status == "published",
                Post.created_at >= since_48h,
                Post.created_at < since_24h,
                Post.id.notin_(existing_ids) if existing_ids else True
            )
            .order_by((Post.comment_count * 3 + Post.like_count * 2 + Post.view_count * 0.2).desc())
            .limit(needed)
        )
        fallback_posts = (await db.execute(q_fallback)).scalars().all()
        posts.extend(fallback_posts)

    return posts

@router.get("/stats/telemetry")
async def get_community_telemetry(
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db)
):
    """底层运行状态看板数据 (仅最高管理员专属权限查看)"""
    cached = await get_cached_json("cache:stats:telemetry")
    if cached is not None:
        return cached

    posts_cnt = (await db.execute(select(func.count(Post.id)).where(Post.status == "published"))).scalar() or 0
    users_cnt = (await db.execute(select(func.count(User.id)).where(User.status.not_in(("banned", "deactivated", "deleting"))))).scalar() or 0
    # 获取真正的 24 小时热议 Top 5
    top_posts = await get_24h_hot_posts(db, limit=5)

    # 1. 真实在线用户数：统计 5 分钟内有认证活动的心跳 key（deps 层每次请求刷新），
    #    远比仅统计聊天页 WS 连接准确
    redis = get_redis()
    real_online = 0
    if redis:
        try:
            async for _ in redis.scan_iter(match="online:user:*", count=200):
                real_online += 1
        except Exception:
            pass
    real_online = max(real_online, 1)

    data = {
        "total_posts": posts_cnt,
        "total_users": users_cnt,
        "online_users": real_online,
        "admin_level": admin.role,
        "top_hot_posts": [{"id": str(p.id), "title": clean_post_title(p.title, getattr(p, "content_md", "")), "comment_count": p.comment_count, "like_count": p.like_count} for p in top_posts]
    }
    await set_cached_json("cache:stats:telemetry", data, ttl=30)
    return data

@router.get("/stats/public")
async def get_public_community_stats(db: AsyncSession = Depends(get_db)):
    """面向全站所有访客和普通用户的公共社区数据"""
    cached = await get_cached_json("cache:stats:public")
    if cached is not None:
        return cached

    posts_cnt = (await db.execute(select(func.count(Post.id)).where(Post.status == "published"))).scalar() or 0
    users_cnt = (await db.execute(select(func.count(User.id)).where(User.status.not_in(("banned", "deactivated", "deleting"))))).scalar() or 0
    # 活跃极客贡献榜 Top 5
    top_users_q = select(User).where(User.status.not_in(("banned", "deactivated", "deleting"))).order_by(User.experience.desc()).limit(5)
    top_users = (await db.execute(top_users_q)).scalars().all()

    # 24 小时热议 Top 5 (真实时间窗口过滤与衰减)
    top_posts = await get_24h_hot_posts(db, limit=5)

    # 动态聚合全站真实文章高频技术标签 (无任何预设假数据，100% 实时提取)
    tag_q = select(Post.tags).where(Post.status == "published", Post.tags.isnot(None), Post.tags != "")
    tags_res = (await db.execute(tag_q)).scalars().all()
    tag_counts = {}
    for t_str in tags_res:
        if not t_str:
            continue
        for part in t_str.replace("，", ",").split(","):
            part_clean = part.strip().lstrip("#")
            if part_clean and len(part_clean) <= 20:
                tag_counts[part_clean] = tag_counts.get(part_clean, 0) + 1

    popular_tags = [t for t, _ in sorted(tag_counts.items(), key=lambda x: x[1], reverse=True)[:10]]

    data = {
        "total_posts": posts_cnt,
        "total_members": users_cnt,
        "popular_tags": popular_tags,
        "top_hot_posts": [{"id": str(p.id), "title": clean_post_title(p.title, getattr(p, "content_md", "")), "comment_count": p.comment_count, "like_count": p.like_count} for p in top_posts],
        "top_contributors": [
            {
                "username": u.username,
                # 补上昵称：榜单此前只能显示账号名，与文章卡片"昵称主 + @用户名副"的规则不一致
                "nickname": u.nickname,
                "avatar_url": u.avatar_url,
                "level": u.level,
                "role": u.role,
                "experience": u.experience
            }
            for u in top_users
        ]
    }
    await set_cached_json("cache:stats:public", data, ttl=60)
    return data


# ---------- 服务端草稿管理与批量状态校验 ----------
@router.get("/drafts")
async def list_user_drafts(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """获取当前用户所有保存的草稿（包含下架转存的草稿）"""
    q = (
        select(Post)
        .options(selectinload(Post.category))
        .where(Post.author_id == user.id, Post.status == "draft")
        .order_by(Post.updated_at.desc())
    )
    res = await db.execute(q)
    drafts = res.scalars().all()
    return [
        {
            "id": str(d.id),
            "title": d.title,
            "content_md": d.content_md,
            "category_id": d.category_id,
            "category_name": d.category.name if d.category else "综合",
            "post_type": d.post_type,
            "tags": [t for t in d.tags.split(",") if t] if d.tags else [],
            "updated_at": format_utc_iso(d.updated_at) or format_utc_iso(d.created_at) or "",
            "created_at": format_utc_iso(d.created_at) or "",
            "char_count": len(d.content_md) if d.content_md else 0
        }
        for d in drafts
    ]

@router.post("/draft/save")
async def save_post_draft(
    req: PostDraftRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """保存或更新当前用户的草稿（严格按 draft_id 独立隔离，绝不覆盖其他草稿）"""
    draft = None
    if req.draft_id:
        draft = (await db.execute(
            select(Post).where(Post.id == req.draft_id, Post.author_id == user.id, Post.status == "draft")
        )).scalar_one_or_none()

    clean_tags = ",".join([t.strip().lstrip('#') for t in req.tags if t.strip()]) if req.tags else ""
    effective_post_type = req.post_type or "post"
    if effective_post_type == "news" and user.role not in ["super_admin", "admin"]:
        effective_post_type = "post"

    if not draft:
        # 当未指定 draft_id 时（或指定的草稿未找到），必须创建新草稿，绝不擅自覆盖用户的其他草稿！
        draft = Post(
            author_id=user.id,
            title=req.title or "未命名草稿",
            content_md=req.content_md or "",
            content_html=clean_html(req.content_md or ""),
            category_id=req.category_id,
            post_type=effective_post_type,
            tags=clean_tags,
            status="draft",
            image_urls=(req.image_urls or None),
            cover_image=(req.image_urls[0] if req.image_urls else None),
            comment_policy=req.comment_policy or "open",
            scheduled_at=req.scheduled_at,
        )
        db.add(draft)
    else:
        # 仅当明确匹配到对应草稿时，才在原草稿上保存修改！
        draft.title = req.title or "未命名草稿"
        draft.content_md = req.content_md or ""
        draft.content_html = clean_html(req.content_md or "")
        draft.category_id = req.category_id
        if req.post_type:
            draft.post_type = effective_post_type
        draft.tags = clean_tags
        if req.image_urls is not None:
            draft.image_urls = req.image_urls or None
            draft.cover_image = req.image_urls[0] if req.image_urls else None
        if req.comment_policy is not None:
            draft.comment_policy = req.comment_policy
        if req.scheduled_at is not None:
            draft.scheduled_at = req.scheduled_at
        draft.updated_at = utc_now()

    await db.flush()
    await mark_images_attached(
        db, draft.id, req.content_md or "", extra_urls=req.image_urls
    )
    await db.commit()
    await db.refresh(draft)
    return {
        "message": "草稿已安全同步至云端",
        "draft_id": str(draft.id),
        "updated_at": format_utc_iso(draft.updated_at) or ""
    }

@router.get("/draft/latest")
async def get_latest_post_draft(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """获取当前用户最近一份服务端保存的草稿"""
    # 用户存在多份草稿时 scalar_one_or_none 会抛 MultipleResultsFound，必须显式取最新一条
    q = (
        select(Post)
        .where(Post.author_id == user.id, Post.status == "draft")
        .order_by(Post.updated_at.desc())
        .limit(1)
    )
    res = await db.execute(q)
    draft = res.scalars().first()
    if not draft:
        return {"has_draft": False}
    return {
        "has_draft": True,
        "draft_id": draft.id,
        "title": draft.title,
        "content_md": draft.content_md,
        "category_id": draft.category_id,
        "tags": [t for t in draft.tags.split(",") if t] if draft.tags else [],
        "image_urls": getattr(draft, "image_urls", None) or [],
        "updated_at": draft.updated_at
    }

@router.post("/batch-status")
async def check_batch_post_status(
    req: BatchStatusRequest,
    user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db)
):
    """批量校验一组文章的存活与可见状态（用于阅读历史自动过滤已删、已下架文章）"""
    if not req.post_ids:
        return {}

    valid_uuids = []
    uuid_map = {}
    for raw_id in req.post_ids:
        try:
            u = UUID(raw_id)
            valid_uuids.append(u)
            uuid_map[u] = raw_id
        except Exception:
            pass

    status_map = {}
    if valid_uuids:
        query = select(Post.id, Post.status, Post.author_id).where(Post.id.in_(valid_uuids))
        res = await db.execute(query)
        rows = res.all()
        for pid, status, author_id in rows:
            raw_key = uuid_map.get(pid, str(pid))
            if status == "deleted":
                status_map[raw_key] = "deleted"
            elif status == "draft":
                if user and (user.id == author_id or is_admin_role(user.role)):
                    status_map[raw_key] = "draft"
                else:
                    status_map[raw_key] = "deleted"
            else:
                status_map[raw_key] = "published"

    for raw_id in req.post_ids:
        if raw_id not in status_map:
            status_map[raw_id] = "deleted"

    return status_map


async def _should_count_view(post_id: UUID, user: Optional[User], client_ip: str) -> bool:
    """浏览量去重：同一访客对同一篇文章 6 小时内只计一次，作者本人不计。

    原先每次 GET 都自增并写库，刷新页面即可刷量，同时放大写压力。
    """
    redis = get_redis()
    if not redis:
        return True
    actor = f"u:{user.id}" if user else f"ip:{client_ip or 'unknown'}"
    try:
        return bool(await redis.set(f"wb:view:{post_id}:{actor}", "1", ex=6 * 3600, nx=True))
    except Exception:
        return True


@router.get("/{post_id}/edits")
async def get_post_edits(
    post_id: UUID,
    user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db)
):
    """文章编辑历史（仅作者与管理员可见）。"""
    post = (await db.execute(select(Post).where(Post.id == post_id))).scalar_one_or_none()
    if not post:
        raise HTTPException(status_code=404, detail="文章不存在")
    if not user or (user.id != post.author_id and not is_admin_role(user.role)):
        raise HTTPException(status_code=403, detail="只有作者或管理员可以查看编辑历史")

    rows = (await db.execute(
        select(PostEdit, User)
        .outerjoin(User, PostEdit.editor_id == User.id)
        .where(PostEdit.post_id == post_id)
        .order_by(PostEdit.created_at.desc())
        .limit(50)
    )).all()

    return [
        {
            "id": e.id,
            "old_title": e.old_title,
            "old_content_md": e.old_content_md,
            "created_at": format_utc_iso(e.created_at),
            "editor_username": u.username if u else None,
            "editor_nickname": u.nickname if u else None,
        }
        for e, u in rows
    ]


@router.get("/{post_id}", response_model=PostDetailResponse)
async def get_post_detail(
    post_id: UUID,
    request: Request,
    user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db)
):
    # 详情缓存：按「文章 + 访问者」维度，30 秒。
    # 点赞 / 收藏 / 评论 / 编辑时会主动删掉对应 key，保证用户态不滞后。
    cache_key = f"cache:post:{post_id}:{user.id if user else 'anon'}"
    cached_detail = await get_cached_json(cache_key)
    if cached_detail:
        try:
            return PostDetailResponse(**cached_detail)
        except Exception:
            pass

    query = select(Post).options(
        selectinload(Post.author),
        selectinload(Post.category),
        selectinload(Post.edits),
        selectinload(Post.poll).selectinload(Poll.options)
    ).where(Post.id == post_id)
    res = await db.execute(query)
    post = res.scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在或已被删除")

    # 可见性白名单：仅 published 对所有人开放；draft / scheduled / deleted 只对作者与管理员可见
    if post.status != "published":
        if not user or (user.id != post.author_id and not is_admin_role(user.role)):
            raise HTTPException(status_code=404, detail="文章不存在或已被删除")

    # 浏览量自增（去重 + 作者本人不计）
    is_author_view = bool(user and user.id == post.author_id)
    if not is_author_view:
        client_ip = request.client.host if request.client else "0.0.0.0"
        if await _should_count_view(post_id, user, client_ip):
            # 原子自增：ORM 读-改-写在多访客并发下会互相覆盖丢计数
            await db.execute(
                update(Post).where(Post.id == post_id).values(view_count=Post.view_count + 1)
            )
            await db.commit()

    is_liked = False
    is_collected = False
    if user:
        like_q = select(Like).where(Like.user_id == user.id, Like.target_type == "post", Like.target_id == post_id)
        is_liked = bool((await db.execute(like_q)).scalar_one_or_none())

        col_q = select(Collection).where(Collection.user_id == user.id, Collection.post_id == post_id)
        is_collected = bool((await db.execute(col_q)).scalar_one_or_none())

    toc, r_time = extract_toc_and_reading_time(post.content_md)
    actual_title = post.title
    if not actual_title or "今日技术头条与架构前沿精选" in actual_title:
        extracted = smart_extract_title_from_md(post.content_md, post.post_type, post.created_at)
        if extracted:
            actual_title = extracted

    poll_detail = await build_poll_detail_response(post.poll, user, db) if post.poll else None

    # 作者在线状态：供「作者名片」的头像绿点使用。
    # 对方开启「隐藏在线状态」时，除本人外一律按离线下发。
    author_online = False
    if post.author and not (
        getattr(post.author, "hide_online_status", False)
        and (not user or user.id != post.author.id)
    ):
        try:
            redis = get_redis()
            if redis:
                author_online = bool(await redis.exists(f"online:user:{post.author_id}"))
        except Exception:
            author_online = False

    payload = PostDetailResponse(
        id=post.id,
        title=actual_title,
        post_type=post.post_type,
        status=post.status,
        comment_policy=post.comment_policy,
        is_pinned=post.is_pinned,
        view_count=post.view_count,
        like_count=post.like_count,
        comment_count=post.comment_count,
        cover_image=post.cover_image,
        image_urls=getattr(post, "image_urls", None),
            display_image=post.cover_image or _first_content_image(post.content_md),
        created_at=post.created_at,
        reading_time_minutes=r_time,
        author_id=post.author_id,
        author_username=post.author.username if post.author else "用户已注销",
        author_nickname=post.author.nickname if post.author else None,
        author_avatar=post.author.avatar_url if post.author else "",
        author_level=post.author.level if post.author else 0,
        author_is_online=author_online,

        category_id=post.category_id,
        category_name=post.category.name if post.category else None,
        content_md=post.content_md,
        content_type=getattr(post, "content_type", "markdown") or "markdown",
        source_type=getattr(post, "source_type", None),
        source_note=getattr(post, "source_note", None),
        content_html=post.content_html,
        is_liked=is_liked,
        is_collected=is_collected,
        tags=[t for t in post.tags.split(",") if t] if post.tags else [],
        toc=toc,
        edits_count=len(post.edits),
        poll_id=post.poll.id if post.poll else None,
        poll=poll_detail
    )
    try:
        await set_cached_json(cache_key, payload.model_dump(mode="json"), ttl=30)
    except Exception:
        pass
    return payload


async def _drop_post_detail_cache(post_id, user_id=None) -> None:
    """点赞/收藏/评论后清掉详情缓存，避免用户态与计数滞后。"""
    try:
        from app.core.database import get_redis

        redis = get_redis()
        if not redis:
            return
        keys = [f"cache:post:{post_id}:anon"]
        if user_id:
            keys.append(f"cache:post:{post_id}:{user_id}")
        await redis.delete(*keys)
    except Exception:
        pass


@router.post("/{post_id}/like")
async def toggle_like_post(
    post_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    query = select(Post).where(Post.id == post_id)
    post = (await db.execute(query)).scalar_one_or_none()
    if not post:
        raise HTTPException(status_code=404, detail="文章不存在")
    assert_post_interactable(post, user)
    await _drop_post_detail_cache(post_id, user.id)

    like_q = select(Like).where(Like.user_id == user.id, Like.target_type == "post", Like.target_id == post_id)
    existing_like = (await db.execute(like_q)).scalar_one_or_none()

    if existing_like:
        await db.delete(existing_like)
        # 原子自减，避免并发读改写丢计数
        await db.execute(
            update(Post).where(Post.id == post_id).values(like_count=func.greatest(Post.like_count - 1, 0))
        )
        is_liked = False
    else:
        db.add(Like(user_id=user.id, target_type="post", target_id=post_id))
        try:
            await db.flush()
        except IntegrityError:
            # 并发重复点赞：唯一约束兜底，按"已点赞"幂等返回
            await db.rollback()
            fresh = await db.scalar(select(Post.like_count).where(Post.id == post_id))
            return {"is_liked": True, "like_count": int(fresh or 0)}
        await db.execute(
            update(Post).where(Post.id == post_id).values(like_count=Post.like_count + 1)
        )
        is_liked = True

    # 经验奖励放最后：add_user_experience 内部会 commit，
    # 先插入点赞记录与通知再触发，保证经验落库时同事务数据一并提交，
    # 避免"作者白拿经验但点赞请求失败"的半提交状态
    if is_liked and post.author_id != user.id:
        notif = Notification(
            receiver_id=post.author_id,
            sender_id=user.id,
            action_type="like_post",
            target_id=post.id,
            brief=f"@{user.nickname or user.username} 赞了你的文章《{post.title[:20]}》"
        )
        db.add(notif)
        author_q = select(User).where(User.id == post.author_id)
        author = (await db.execute(author_q)).scalar_one_or_none()
        if author:
            await add_user_experience(author, 2, db)

    await db.commit()
    await invalidate_posts_cache()
    fresh_count = await db.scalar(select(Post.like_count).where(Post.id == post_id))
    return {"is_liked": is_liked, "like_count": int(fresh_count or 0)}

# 每用户每日图片上传配额：单张最大 10MB，50 张 ≈ 500MB/天，
# 防止脚本循环上传把磁盘打满（nginx 的 30r/s 只能防瞬时突发，防不住持续占用）
DAILY_UPLOAD_LIMIT = 50


async def _check_upload_quota(user: User) -> Optional[str]:
    """上传前原子预占当日额度，返回计数用的 Redis key。

    之前是「先 get 再 incr」，并发上传会同时读到未超限而一起放行。
    现在直接 incr 预占：超过上限立刻 decr 归还并拒绝；
    上传失败（格式/体积校验不过）由调用方走 _release_upload_quota 归还额度。
    """
    redis = get_redis()
    if not redis:
        return None
    try:
        # 按北京时间切日：UTC 切日会让北京时间 08:00 前后各拿一份额度
        from zoneinfo import ZoneInfo
        tz = ZoneInfo("Asia/Shanghai")
        now_local = datetime.now(tz)
        day = now_local.strftime("%Y%m%d")
        key = f"wb:upload:daily:{user.id}:{day}"
        used = int(await redis.incr(key))
        if used == 1:
            # TTL 到当日 23:59:59，而不是「首次上传起 86400 秒」
            end_of_day = now_local.replace(hour=23, minute=59, second=59, microsecond=0)
            ttl = max(60, int((end_of_day - now_local).total_seconds()))
            await redis.expire(key, ttl)
        if used > DAILY_UPLOAD_LIMIT:
            await redis.decr(key)
            raise HTTPException(
                status_code=429,
                detail=f"今日图片上传已达上限（{DAILY_UPLOAD_LIMIT} 张），请明天再试",
            )
        return key
    except HTTPException:
        raise
    except Exception:
        # fail-closed：Redis 抖动时宁可短暂拒绝上传，也不能让每日配额失效
        raise HTTPException(status_code=503, detail="上传额度服务暂时不可用，请稍后再试")


async def _release_upload_quota(key: Optional[str]) -> None:
    """上传失败时归还预占的额度。"""
    if not key:
        return
    redis = get_redis()
    if not redis:
        return
    try:
        await redis.decr(key)
    except Exception:
        return


@router.post("/upload-image")
async def upload_post_image(
    file: UploadFile = File(...),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    quota_key = await _check_upload_quota(user)
    try:
        url, size = await save_upload_image(file, str(user.id))
    except Exception:
        # 落盘失败（格式/体积不合规等）不消耗当天额度
        await _release_upload_quota(quota_key)
        raise
    # 状态机: 记录为 temp，由定时任务防爆兜底
    img_record = PostImage(
        uploader_id=user.id,
        storage_path=url,
        file_size=size,
        status="temp"
    )
    db.add(img_record)
    try:
        await db.commit()
    except Exception:
        # DB 写入失败时归还刚预占的额度，避免用户当天白掉一次上传机会
        await db.rollback()
        await _release_upload_quota(user)
        raise HTTPException(status_code=503, detail="保存图片记录失败，请稍后重试")
    return {"url": url}

@router.post("/{post_id}/collect")
async def toggle_collect_post(
    post_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    query = select(Post).where(Post.id == post_id)
    post = (await db.execute(query)).scalar_one_or_none()
    if not post:
        raise HTTPException(status_code=404, detail="文章不存在")
    assert_post_interactable(post, user)

    col_q = select(Collection).where(Collection.user_id == user.id, Collection.post_id == post_id)
    existing_col = (await db.execute(col_q)).scalar_one_or_none()

    if existing_col:
        await db.delete(existing_col)
        await db.execute(
            update(Post).where(Post.id == post_id).values(
                collect_count=func.greatest(func.coalesce(Post.collect_count, 0) - 1, 0)
            )
        )
        is_collected = False
    else:
        db.add(Collection(user_id=user.id, post_id=post_id))
        try:
            await db.flush()
        except IntegrityError:
            await db.rollback()
            fresh = await db.scalar(select(Post.collect_count).where(Post.id == post_id))
            return {"is_collected": True, "collect_count": int(fresh or 0)}
        await db.execute(
            update(Post).where(Post.id == post_id).values(
                collect_count=func.coalesce(Post.collect_count, 0) + 1
            )
        )
        is_collected = True

    await db.commit()
    fresh_collect = await db.scalar(select(Post.collect_count).where(Post.id == post_id))
    return {"is_collected": is_collected, "collect_count": int(fresh_collect or 0)}

@router.put("/{post_id}", response_model=PostDetailResponse)
async def update_post(
    post_id: UUID,
    req: PostUpdateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    query = select(Post).options(selectinload(Post.author), selectinload(Post.category), selectinload(Post.edits), selectinload(Post.poll).selectinload(Poll.options)).where(Post.id == post_id)
    res = await db.execute(query)
    post = res.scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在或已被删除")

    if post.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权编辑此文章")

    # 仅当已发布文章的标题或正文实际发生变更时记录历史编辑版本
    if post.status == "published":
        if (req.title is not None and req.title != post.title) or (req.content_md is not None and req.content_md != post.content_md):
            edit_record = PostEdit(
                post_id=post.id,
                editor_id=user.id,
                old_title=post.title,
                old_content_md=post.content_md
            )
            db.add(edit_record)

    if req.title is not None:
        is_blocked, hit_words, replaced_title = sensitive_filter.scan(req.title)
        if is_blocked:
            raise HTTPException(status_code=400, detail=f"标题包含违规敏感词: {', '.join(hit_words)}")
        post.title = replaced_title

    if req.content_md is not None:
        is_blocked_c, hit_words_c, replaced_content = sensitive_filter.scan(req.content_md)
        if is_blocked_c:
            raise HTTPException(status_code=400, detail=f"正文包含违规敏感词: {', '.join(hit_words_c)}")
        post.content_md = replaced_content
        post.content_html = clean_html(replaced_content)

    if req.category_id is not None:
        cat_q = select(Category).where(Category.id == req.category_id)
        cat_obj = (await db.execute(cat_q)).scalar_one_or_none()
        if cat_obj:
            post.category_id = cat_obj.id

    if req.cover_image is not None:
        post.cover_image = req.cover_image
    if req.image_urls is not None:
        if req.image_urls:
            await _check_album_weekly_limit(db, user, exclude_post_id=post.id)
        post.image_urls = req.image_urls or None
        # 封面跟着第一张走，保证列表图与九宫格一致
        post.cover_image = req.image_urls[0] if req.image_urls else None

    if req.comment_policy is not None:
        post.comment_policy = req.comment_policy

    if req.tags is not None:
        post.tags = ",".join([t.strip().lstrip('#') for t in req.tags if t.strip()])

    # 处理附加投票的创建与更新
    if req.poll and getattr(req.poll, "options", None) and len(req.poll.options) >= 2:
        poll_q = select(Poll).where(Poll.post_id == post.id)
        existing_poll = (await db.execute(poll_q)).scalar_one_or_none()
        if not existing_poll:
            duration = getattr(req.poll, "duration_days", 7) or 7
            new_poll = Poll(
                post_id=post.id,
                title=req.poll.title,
                is_multiple=req.poll.is_multiple,
                max_choices=req.poll.max_choices,
                expires_at=datetime.now(timezone.utc) + timedelta(days=duration)
            )
            db.add(new_poll)
            await db.flush()
            for opt_text in req.poll.options:
                if opt_text and str(opt_text).strip():
                    poll_opt = PollOption(poll_id=new_poll.id, option_text=str(opt_text).strip())
                    db.add(poll_opt)
        else:
            existing_poll.title = req.poll.title
            existing_poll.is_multiple = req.poll.is_multiple
            existing_poll.max_choices = req.poll.max_choices
            vote_cnt = (await db.execute(select(func.count(PollVote.id)).where(PollVote.poll_id == existing_poll.id))).scalar() or 0
            if vote_cnt == 0:
                await db.execute(delete(PollOption).where(PollOption.poll_id == existing_poll.id))
                await db.flush()
                for opt_text in req.poll.options:
                    if opt_text and str(opt_text).strip():
                        poll_opt = PollOption(poll_id=existing_poll.id, option_text=str(opt_text).strip())
                        db.add(poll_opt)

    # 供发布后 AI 即时触发判定使用（req.status 为空时也要有值）
    is_publishing = False
    is_first_publish = False
    if req.status is not None and req.status in ["published", "draft", "scheduled"]:
        is_publishing = False
        # 定时发布：设置到点自动上线的时间，到点前对他人不可见（仅管理员可用）
        if req.status == "scheduled":
            if user.role not in ("super_admin", "admin"):
                raise HTTPException(status_code=403, detail="定时发布为管理员专属能力，普通用户请直接发布或保存草稿")
            if not req.scheduled_at:
                raise HTTPException(status_code=400, detail="定时发布必须指定发布时间")
            sched = req.scheduled_at if req.scheduled_at.tzinfo else req.scheduled_at.replace(tzinfo=timezone.utc)
            if sched <= datetime.now(timezone.utc):
                raise HTTPException(status_code=400, detail="定时发布时间必须晚于当前时间")
            post.status = "scheduled"
            post.scheduled_at = sched
        # 任何"非公开 → 公开"的状态跃迁都视为发布动作，必须重新走一遍发布校验
        elif post.status != "published" and req.status == "published":
            is_publishing = True
        else:
            is_publishing = False

        if is_publishing:
            # 草稿里囤多篇九宫格、只改 status 发布 —— 此前完全绕过每周一次的限制。
            # 统计对象必须是帖子作者，而不是当前操作者（管理员代操作时口径才正确）。
            if getattr(post, "image_urls", None):
                album_author = (await db.execute(
                    select(User).where(User.id == post.author_id)
                )).scalar_one_or_none()
                if album_author:
                    await _check_album_weekly_limit(db, album_author, exclude_post_id=post.id)

            if user.status == "probation":
                raise HTTPException(
                    status_code=403,
                    detail="当前账号处于见习观察期（累计活跃 5 天后自动转正），见习期间暂未开放公开发布文章特权，欢迎在社区积极互动学习～"
                )
            if not (post.title or "").strip() or not (post.content_md or "").strip():
                raise HTTPException(status_code=400, detail="草稿内容为空，无法直接发布上架，请先完善标题与正文")

            # 防刷：与 create_post 相同的 20 秒冷却与每日限额同样约束"草稿→发布"路径，
            # 堵住"下架转草稿再发布"循环刷经验/绕限额/顶热榜的漏洞
            if user.role not in ("super_admin", "admin"):
                latest_post_res = await db.execute(
                    select(Post.created_at)
                    .where(Post.author_id == user.id, Post.status == "published")
                    .order_by(Post.created_at.desc())
                    .limit(1)
                )
                latest_created_at = latest_post_res.scalar_one_or_none()
                if latest_created_at:
                    now_utc = datetime.now(timezone.utc)
                    latest_dt = latest_created_at if latest_created_at.tzinfo else latest_created_at.replace(tzinfo=timezone.utc)
                    diff_seconds = (now_utc - latest_dt).total_seconds()
                    if 0 <= diff_seconds < 20:
                        raise HTTPException(
                            status_code=429,
                            detail=f"发帖过于频繁，防刷保护已生效，请等待 {math.ceil(20 - diff_seconds)} 秒后再发布"
                        )

                beijing_tz = timezone(timedelta(hours=8))
                now_bj = datetime.now(beijing_tz)
                start_of_day_bj = datetime(now_bj.year, now_bj.month, now_bj.day, 0, 0, 0, tzinfo=beijing_tz)
                start_of_day_utc = start_of_day_bj.astimezone(timezone.utc)
                daily_post_limit = await get_system_setting_int(db, "daily_post_limit", 5)
                today_posts_cnt_res = await db.execute(
                    select(func.count(Post.id)).where(
                        Post.author_id == user.id,
                        Post.status == "published",
                        Post.created_at >= start_of_day_utc
                    )
                )
                if (today_posts_cnt_res.scalar() or 0) >= daily_post_limit:
                    raise HTTPException(
                        status_code=400,
                        detail=f"您今日发帖数量已达上限（每日限发 {daily_post_limit} 篇），请明天再来交流～"
                    )

            # 关键：草稿/定时文章此前可能已绕过敏感词检查（存草稿不扫描），
            # 发布瞬间必须对库中现有标题与正文重新审查，堵住"只改 status"的绕过路径
            blocked_t, hits_t, clean_title = sensitive_filter.scan(post.title or "")
            if blocked_t:
                raise HTTPException(status_code=400, detail=f"标题包含违规敏感词: {', '.join(hits_t)}")
            blocked_c, hits_c, clean_content = sensitive_filter.scan(post.content_md or "")
            if blocked_c:
                raise HTTPException(status_code=400, detail=f"正文包含违规敏感词: {', '.join(hits_c)}")
            post.title = clean_title
            if clean_content != (post.content_md or ""):
                post.content_md = clean_content
                post.content_html = clean_html(clean_content)

            is_first_publish = post.first_published_at is None
            post.status = "published"
            if is_first_publish:
                post.first_published_at = utc_now()
                # 仅首次发布刷新 created_at（保住首页时效位），重发布不再顶热榜
                post.created_at = post.first_published_at
                clean_text = re.sub(r'[\s#*`\-_~>|\[\]\(\)!]+', '', post.content_md or "")
                if len(clean_text) >= 15:
                    post_exp_reward = await get_system_setting_int(db, "post_exp_reward", 15)
                    await add_user_experience(user, post_exp_reward, db)
            await record_user_activity(user, db)
        else:
            post.status = req.status

    await mark_images_attached(
        db, post.id,
        f"{post.content_md or ''} {post.cover_image or ''}",
        extra_urls=getattr(post, "image_urls", None),
    )
    post.updated_at = utc_now()
    # 草稿转发布时若分类是问答广场，补建悬赏记录
    if is_publishing and is_first_publish and post.category_id:
        from app.models.post import Category as _QCat2
        _qcat2 = (await db.execute(
            select(_QCat2).where(_QCat2.id == post.category_id)
        )).scalar_one_or_none()
        if _qcat2 and _qcat2.slug == "qa":
            from app.services.qa_service import create_qa_question, qa_state
            if not await qa_state(db, post.id, None):
                await create_qa_question(db, post, user)

    await db.commit()
    await invalidate_posts_cache()

    # 草稿/定时转正式发布（仅首次）也立即触发一条 AI 评论
    if is_publishing and is_first_publish and post.post_type != "news":
        from app.services.ai_service import schedule_ai_comment
        schedule_ai_comment(str(post.id))

    await maybe_unlock_word_tribulation(db, user, post)

    # 安全重新载入完整的 post 及其所有关联属性，彻底规避 refresh 导致关联被清空后懒加载 MissingGreenlet 异常
    reload_q = (
        select(Post)
        .options(
            selectinload(Post.author),
            selectinload(Post.category),
            selectinload(Post.edits),
            selectinload(Post.poll).selectinload(Poll.options),
        )
        .where(Post.id == post_id)
    )
    post = (await db.execute(reload_q)).scalar_one()

    poll_detail = await build_poll_detail_response(post.poll, user, db) if post.poll else None
    toc, r_time = extract_toc_and_reading_time(post.content_md)
    return PostDetailResponse(
        id=post.id,
        title=post.title,
        post_type=post.post_type,
        status=post.status,
        comment_policy=post.comment_policy,
        is_pinned=post.is_pinned,
        is_essence=bool(getattr(post, 'is_essence', False)),
        accepted_comment_id=getattr(post, 'accepted_comment_id', None),
        view_count=post.view_count,
        like_count=post.like_count,
        comment_count=post.comment_count,
        cover_image=post.cover_image,
        image_urls=getattr(post, "image_urls", None),
            display_image=post.cover_image or _first_content_image(post.content_md),
        created_at=post.created_at,
        reading_time_minutes=r_time,
        author_id=post.author_id,
        author_username=post.author.username if post.author else "用户已注销",
        author_nickname=post.author.nickname if post.author else None,
        author_avatar=post.author.avatar_url if post.author else "",
        author_level=post.author.level if post.author else 0,
        category_id=post.category_id,
        category_name=post.category.name if post.category else None,
        content_md=post.content_md,
        content_type=getattr(post, "content_type", "markdown") or "markdown",
        source_type=getattr(post, "source_type", None),
        source_note=getattr(post, "source_note", None),
        content_html=post.content_html,
        is_liked=False,
        is_collected=False,
        tags=[t for t in post.tags.split(",") if t] if post.tags else [],
        toc=toc,
        edits_count=len(post.edits),
        poll_id=post.poll.id if post.poll else None,
        poll=poll_detail
    )

@router.delete("/{post_id}")
async def delete_post(
    post_id: UUID,
    action: str = Query("draft", pattern=r"^(draft|delete)$"),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    文章下架与删除接口：
    action=draft (默认): 下架文章并安全存入草稿箱，作者可后续编辑再发布
    action=delete: 彻底清除删除文章
    """
    query = select(Post).options(selectinload(Post.author)).where(Post.id == post_id)
    res = await db.execute(query)
    post = res.scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在或已被删除")

    if post.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权操作此文章")

    # 问答广场：删除/下架时把未结算悬赏原路退回系统经验池
    from app.services.qa_service import refund_question
    await refund_question(db, post_id, "deleted")

    if action == "draft":
        post.status = "draft"
        # 清理下架残留状态：置顶标记与定时发布时间若保留，重新发布会瞬间恢复置顶/产生状态歧义
        post.is_pinned = False
        post.scheduled_at = None
        post.updated_at = utc_now()
        await db.commit()
        await invalidate_posts_cache()
        return {
            "success": True,
            "message": "文章已成功下架并安全转存至草稿箱，您可以随时在编辑器草稿箱中重新编辑发布！",
            "status": "draft"
        }
    else:
        post.status = "deleted"
        await db.commit()
        await invalidate_posts_cache()
        return {"success": True, "message": "文章已彻底删除", "status": "deleted"}

@router.post("/{post_id}/pin")
async def toggle_pin_post(
    post_id: UUID,
    admin: User = Depends(get_current_moderator),
    db: AsyncSession = Depends(get_db)
):
    """板块版主及以上特权：一键置顶 / 取消置顶"""
    query = select(Post).where(Post.id == post_id)
    post = (await db.execute(query)).scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在")

    post.is_pinned = not post.is_pinned
    await db.commit()
    await invalidate_posts_cache()
    return {"is_pinned": post.is_pinned, "message": "文章已设为置顶" if post.is_pinned else "已取消置顶"}

@router.post("/{post_id}/essence")
async def toggle_post_essence(
    post_id: UUID,
    admin: User = Depends(get_current_moderator),
    db: AsyncSession = Depends(get_db)
):
    """板块版主及以上特权：设为精华推荐 (Staff Pick)"""
    query = select(Post).where(Post.id == post_id)
    post = (await db.execute(query)).scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在")

    post.is_essence = not getattr(post, 'is_essence', False)
    await db.commit()

    if post.is_essence and post.author_id:
        notif = Notification(
            receiver_id=post.author_id,
            sender_id=admin.id,
            action_type="essence_post",
            target_id=post.id,
            brief=f"恭喜！您的文章《{post.title[:30]}》被评选为社区精华推荐 (Staff Pick)！"
        )
        db.add(notif)
        await db.commit()

    return {"is_essence": post.is_essence, "message": "已设为精华推荐" if post.is_essence else "已取消精华推荐"}

@router.post("/{post_id}/lock")
async def toggle_lock_post(
    post_id: UUID,
    admin: User = Depends(get_current_moderator),
    db: AsyncSession = Depends(get_db)
):
    """板块版主及以上特权：锁定 / 开放评论区"""
    query = select(Post).where(Post.id == post_id)
    post = (await db.execute(query)).scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在")

    post.comment_policy = "closed" if post.comment_policy != "closed" else "open"
    await db.commit()
    return {"comment_policy": post.comment_policy, "message": "评论区已被版主锁定" if post.comment_policy == "closed" else "评论区已恢复开放"}


@router.post("/{post_id}/category")
async def move_post_category(
    post_id: UUID,
    payload: PostMoveCategoryRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """换区：作者本人可调整自己帖子的板块，管理员 / 版主可调整任意帖子的板块"""
    query = select(Post).where(Post.id == post_id)
    post = (await db.execute(query)).scalar_one_or_none()
    if not post or post.status == "deleted":
        raise HTTPException(status_code=404, detail="文章不存在")

    # 作者本人，或管理团队（超管 / 管理员 / 版主）
    is_staff = user.role in ("super_admin", "admin", "moderator")
    if post.author_id != user.id and not is_staff:
        raise HTTPException(status_code=403, detail="只有作者本人或管理员可以调整文章所属板块")

    target_cat = (
        await db.execute(select(Category).where(Category.id == payload.category_id))
    ).scalar_one_or_none()
    if not target_cat:
        raise HTTPException(status_code=404, detail="目标板块不存在，请刷新后重试")

    if post.category_id == target_cat.id:
        return {
            "category_id": target_cat.id,
            "category_name": target_cat.name,
            "message": f"文章已在「{target_cat.name}」板块，无需调整",
        }

    # 普通用户对同一篇帖子的换区次数有上限（管理员 / 版主不限，也不消耗作者的额度）
    redis = get_redis()
    move_key = f"post:catmove:{post_id}"
    if not is_staff:
        used_raw = await redis.get(move_key)
        used_moves = int(used_raw) if used_raw else 0
        if used_moves >= CATEGORY_MOVE_LIMIT:
            raise HTTPException(
                status_code=403,
                detail=f"该文章的换区次数已达上限（{CATEGORY_MOVE_LIMIT} 次），如需继续调整请联系管理员",
            )

    old_cat_name = None
    if post.category_id:
        old_cat = (
            await db.execute(select(Category).where(Category.id == post.category_id))
        ).scalar_one_or_none()
        old_cat_name = old_cat.name if old_cat else None

    post.category_id = target_cat.id
    post.updated_at = utc_now()
    await db.commit()
    # 板块变更会牵动首页分类流、热度榜与推荐池的分组，必须清缓存
    await invalidate_posts_cache()

    remaining_moves = None
    if not is_staff:
        new_used = await redis.incr(move_key)
        if new_used == 1:
            # 计数跟随帖子生命周期，给个长 TTL 防止键无限堆积
            await redis.expire(move_key, 365 * 24 * 3600)
        remaining_moves = max(0, CATEGORY_MOVE_LIMIT - new_used)

    message = (
        f"已将文章从「{old_cat_name}」调整到「{target_cat.name}」板块"
        if old_cat_name
        else f"已将文章归入「{target_cat.name}」板块"
    )
    if remaining_moves is not None:
        message += f"，还可调整 {remaining_moves} 次"

    return {
        "category_id": target_cat.id,
        "category_name": target_cat.name,
        "remaining_moves": remaining_moves,
        "message": message,
    }



# ---------- 专栏 / 体系化文集合集 ----------
@router.get("/series/all", response_model=List[PostSeriesResponse])
async def list_all_series(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=50),
    db: AsyncSession = Depends(get_db)
):
    """公开技术专栏合集列表"""
    q = select(PostSeries).options(selectinload(PostSeries.items).selectinload(SeriesItem.post)).order_by(PostSeries.created_at.desc()).offset((page - 1) * size).limit(size)
    res = await db.execute(q)
    all_series = res.scalars().all()
    
    result = []
    for s in all_series:
        author_res = await db.execute(select(User).where(User.id == s.author_id))
        author = author_res.scalar_one_or_none()
        chapter_items = []
        for item in (s.items or []):
            if item.post and item.post.status == "published":
                chapter_items.append(SeriesItemResponse(
                    id=item.id,
                    post_id=item.post_id,
                    post_title=item.post.title,
                    sort_order=item.sort_order
                ))
        result.append(PostSeriesResponse(
            id=s.id,
            author_id=s.author_id,
            author_username=author.username if author else "官方",
            author_nickname=author.nickname if author else None,
            title=s.title,
            description=s.description or "",
            cover_image=s.cover_image,
            created_at=s.created_at,
            chapter_count=len(chapter_items),
            items=chapter_items
        ))
    return result

@router.post("/series")
async def create_series(
    req: PostSeriesCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """创建新技术专栏"""
    new_s = PostSeries(
        author_id=user.id,
        title=req.title,
        description=req.description or "",
        cover_image=req.cover_image
    )
    db.add(new_s)
    await db.commit()
    await db.refresh(new_s)
    return {"id": new_s.id, "message": "技术专栏已成功创建"}

@router.post("/series/{series_id}/items")
async def add_post_to_series(
    series_id: UUID,
    req: PostSeriesItemAddRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """将文章编入指定专栏章节"""
    s_q = select(PostSeries).where(PostSeries.id == series_id)
    series = (await db.execute(s_q)).scalar_one_or_none()
    if not series:
        raise HTTPException(status_code=404, detail="专栏不存在")
    if series.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权修改该专栏")

    # 归属与状态校验：此前可编入他人文章、不存在的 UUID 或重复添加
    target_post = (await db.execute(select(Post).where(Post.id == req.post_id))).scalar_one_or_none()
    if not target_post or target_post.status != "published":
        raise HTTPException(status_code=404, detail="要编入的文章不存在或未发布")
    if target_post.author_id != series.author_id:
        raise HTTPException(status_code=403, detail="只能将自己的文章编入专栏")

    dup_q = select(SeriesItem).where(SeriesItem.series_id == series_id, SeriesItem.post_id == req.post_id)
    if (await db.execute(dup_q)).scalar_one_or_none():
        raise HTTPException(status_code=400, detail="该文章已在专栏目录中")

    item = SeriesItem(
        series_id=series_id,
        post_id=req.post_id,
        sort_order=req.sort_order or 0
    )
    db.add(item)
    await db.commit()
    return {"message": "文章已成功编入该专栏目录"}



# ---------- 相似文章推荐（多维加权打分） ----------
@router.get("/{post_id}/related")
async def get_related_posts(
    post_id: UUID,
    limit: int = Query(4, ge=1, le=12),
    db: AsyncSession = Depends(get_db),
):
    """相似文章推荐。

    打分维度（权重和为 1）：
      0.40 标签重合度 (Jaccard)   —— 内容站最强的相关性信号
      0.25 标题相似度 (trigram)   —— 捕捉同主题但标签缺失的情况
      0.15 同分类
      0.12 时效衰减 exp(-天数/45)
      0.08 热度 log(1+浏览+2赞+3评论) 归一化

    另外做作者去重：同一作者最多入选 1 篇，避免刷屏。
    """
    from difflib import SequenceMatcher

    target = (await db.execute(select(Post).where(Post.id == post_id))).scalar_one_or_none()
    if not target:
        raise HTTPException(status_code=404, detail="文章不存在")

    def split_tags(raw: Optional[str]) -> set:
        if not raw:
            return set()
        return {t.strip().lower() for t in str(raw).replace("，", ",").split(",") if t.strip()}

    target_tags = split_tags(target.tags)
    target_title = (target.title or "").strip()

    # 候选池：近 180 天已发布，排除自己；同分类或标签有交集
    since = utc_now() - timedelta(days=180)
    conditions = [Post.status == "published", Post.id != target.id, Post.created_at >= since]
    if target.category_id:
        conditions.append(Post.category_id == target.category_id)
    candidates = (
        await db.execute(
            select(Post)
            .where(and_(*conditions))
            .order_by(desc(Post.created_at))
            .limit(200)
        )
    ).scalars().all()

    if not candidates:
        return {"items": []}

    max_hot = max(
        math.log1p((c.view_count or 0) + 2 * (c.like_count or 0) + 3 * (c.comment_count or 0))
        for c in candidates
    ) or 1.0
    now = utc_now()

    scored = []
    for c in candidates:
        c_tags = split_tags(c.tags)
        union = target_tags | c_tags
        jaccard = (len(target_tags & c_tags) / len(union)) if union else 0.0
        title_sim = SequenceMatcher(None, target_title, (c.title or "")).ratio()
        same_cat = 1.0 if (target.category_id and c.category_id == target.category_id) else 0.0
        days = max(0.0, (now - (c.created_at or now)).total_seconds() / 86400)
        recency = math.exp(-days / 45)
        hot_raw = math.log1p((c.view_count or 0) + 2 * (c.like_count or 0) + 3 * (c.comment_count or 0))
        hot = hot_raw / max_hot

        score = (
            0.40 * jaccard
            + 0.25 * title_sim
            + 0.15 * same_cat
            + 0.12 * recency
            + 0.08 * hot
        )

        shared = sorted(target_tags & c_tags)
        if shared:
            reason = f"标签重合：{shared[0]}"
        elif same_cat:
            reason = "同分类"
        else:
            reason = "标题相近"

        scored.append((score, reason, c))

    scored.sort(key=lambda x: x[0], reverse=True)

    items, seen_authors = [], set()
    for score, reason, c in scored:
        if c.author_id in seen_authors:
            continue
        seen_authors.add(c.author_id)
        items.append(
            {
                "id": str(c.id),
                "title": c.title,
                "comment_count": c.comment_count or 0,
                "score": round(score, 4),
                "reason": reason,
            }
        )
        if len(items) >= limit:
            break

    return {"items": items}


class CollectToFolderPayload(BaseModel):
    folder: str = Field(default="默认收藏", max_length=32)


@router.post("/{post_id}/collect-to")
async def collect_post_to_folder(
    post_id: UUID,
    payload: CollectToFolderPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """收藏到指定收藏夹（幂等）；已收藏时只移动分组，不会误取消。"""
    post = (await db.execute(select(Post).where(Post.id == post_id))).scalar_one_or_none()
    if not post:
        raise HTTPException(status_code=404, detail="文章不存在")
    assert_post_interactable(post, user)

    name = (payload.folder or "默认收藏").strip()[:32] or "默认收藏"
    col = (await db.execute(
        select(Collection).where(Collection.user_id == user.id, Collection.post_id == post_id)
    )).scalar_one_or_none()

    if col:
        col.group_name = name
    else:
        db.add(Collection(user_id=user.id, post_id=post_id, group_name=name))
        try:
            await db.flush()
        except IntegrityError:
            await db.rollback()
            col = (await db.execute(
                select(Collection).where(Collection.user_id == user.id, Collection.post_id == post_id)
            )).scalar_one_or_none()
            if col:
                col.group_name = name
        else:
            await db.execute(
                update(Post).where(Post.id == post_id).values(
                    collect_count=func.coalesce(Post.collect_count, 0) + 1
                )
            )
    await db.commit()
    fresh = (await db.execute(
        select(Post.collect_count).where(Post.id == post_id)
    )).scalar_one() or 0
    return {"is_collected": True, "group_name": name, "collect_count": int(fresh)}


@router.delete("/{post_id}/collect")
async def remove_collection(
    post_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """取消收藏（幂等）。"""
    col = (await db.execute(
        select(Collection).where(Collection.user_id == user.id, Collection.post_id == post_id)
    )).scalar_one_or_none()
    if not col:
        return {"is_collected": False}
    await db.execute(delete(Collection).where(Collection.id == col.id))
    await db.execute(
        update(Post).where(Post.id == post_id).values(
            collect_count=func.greatest(func.coalesce(Post.collect_count, 0) - 1, 0)
        )
    )
    await db.commit()
    return {"is_collected": False}


@router.delete("/series/{series_id}/items/{post_id}")
async def remove_post_from_series(
    series_id: UUID,
    post_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """从专栏目录移除一章（作者或管理员）。"""
    series = (await db.execute(
        select(PostSeries).where(PostSeries.id == series_id)
    )).scalar_one_or_none()
    if not series:
        raise HTTPException(status_code=404, detail="专栏不存在")
    if series.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权修改该专栏")
    item = (await db.execute(
        select(SeriesItem).where(SeriesItem.series_id == series_id, SeriesItem.post_id == post_id)
    )).scalar_one_or_none()
    if not item:
        raise HTTPException(status_code=404, detail="该文章不在专栏目录中")
    await db.execute(delete(SeriesItem).where(SeriesItem.id == item.id))
    await db.commit()
    return {"ok": True}


@router.delete("/series/{series_id}")
async def delete_series(
    series_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """删除整个专栏（章节关联一并级联删除，文章本身保留）。"""
    series = (await db.execute(
        select(PostSeries).where(PostSeries.id == series_id)
    )).scalar_one_or_none()
    if not series:
        raise HTTPException(status_code=404, detail="专栏不存在")
    if series.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权删除该专栏")
    await db.execute(delete(SeriesItem).where(SeriesItem.series_id == series_id))
    await db.execute(delete(PostSeries).where(PostSeries.id == series_id))
    await db.commit()
    return {"ok": True}


class SeriesUpdatePayload(BaseModel):
    title: Optional[str] = Field(None, min_length=2, max_length=100)
    description: Optional[str] = Field(None, max_length=500)
    cover_image: Optional[str] = None


@router.put("/series/{series_id}")
async def update_series(
    series_id: UUID,
    payload: SeriesUpdatePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """编辑专栏标题 / 简介 / 封面（作者或管理员）。"""
    series = (await db.execute(
        select(PostSeries).where(PostSeries.id == series_id)
    )).scalar_one_or_none()
    if not series:
        raise HTTPException(status_code=404, detail="专栏不存在")
    if series.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权修改该专栏")
    if payload.title is not None:
        series.title = payload.title.strip()
    if payload.description is not None:
        series.description = payload.description.strip()
    if payload.cover_image is not None:
        series.cover_image = payload.cover_image or None
    await db.commit()
    return {"ok": True}


class SeriesOrderPayload(BaseModel):
    post_ids: List[UUID] = Field(..., max_length=200)


@router.put("/series/{series_id}/items/order")
async def reorder_series_items(
    series_id: UUID,
    payload: SeriesOrderPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """按传入顺序重排章节。"""
    series = (await db.execute(
        select(PostSeries).where(PostSeries.id == series_id)
    )).scalar_one_or_none()
    if not series:
        raise HTTPException(status_code=404, detail="专栏不存在")
    if series.author_id != user.id and not is_admin_role(user.role):
        raise HTTPException(status_code=403, detail="无权修改该专栏")
    for idx, pid in enumerate(payload.post_ids):
        await db.execute(
            update(SeriesItem)
            .where(SeriesItem.series_id == series_id, SeriesItem.post_id == pid)
            .values(sort_order=idx)
        )
    await db.commit()
    return {"ok": True, "count": len(payload.post_ids)}
