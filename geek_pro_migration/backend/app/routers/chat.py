import asyncio
import json
import random
import re
import time
import uuid
from uuid import UUID, uuid4
from datetime import datetime, timezone, timedelta, date
from typing import List, Optional, Any, Union
from fastapi import APIRouter, Depends, HTTPException, Request, Response, WebSocket, WebSocketDisconnect, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, or_, and_, desc, delete, func, update, true
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import selectinload
from app.core.database import get_db, AsyncSessionLocal
from app.core.security import decode_token
from app.models.chat import (
    Conversation,
    Message,
    GroupRoom,
    GroupMessage,
    GroupMember,
    GroupJoinRequest,
    GroupAuditLog,
    GroupAnnouncement,
    GroupAnnouncementRead,
    GroupCheckin,
    GroupSpecialBadge,
)
from app.models.user import User, Follow, UserBlock
from app.models.system import Notification
from app.schemas.interaction import (
    ConversationResponse,
    MessageResponse,
    MessageCreateRequest,
    GroupRoomCreateRequest,
    GroupRoomUpdateRequest,
    GroupRoomResponse,
    GroupJoinApplyRequest,
    GroupJoinRequestResponse,
    GroupMemberResponse,
    GroupInviteRequest,
    GroupKickRequest,
    GroupMemberRoleRequest,
    GroupTransferOwnerRequest,
    GroupMessageResponse,
    GroupAnnouncementCreateRequest,
    GroupAnnouncementResponse,
    MentionAllQuotaResponse,
    GroupMuteRequest,
    GroupSpecialBadgeRequest,
    GroupWearBadgeRequest,
    GroupCheckinConfigRequest,
    GroupBadgeRequest,
    GroupBadgeModeRequest,
)
from app.routers.deps import get_current_user, get_current_active_user
from app.services.ws_manager import ws_manager
from app.services.sensitive_service import sensitive_filter
from app.services.image_quota import is_image_message, is_sticker_content

router = APIRouter(prefix="/chat", tags=["实时私信"])

# 单条消息最大长度 (HTTP 与 WebSocket 两条通道保持完全一致)
MAX_MESSAGE_LEN = 5000
# 群聊消息分页默认大小，前端据此判断是否还有更早历史
GROUP_PAGE_SIZE = 50

def _preview_text(content: str, limit: int = 50) -> str:
    """会话列表预览：先把图片/表情地址折叠成占位词，再截断。

    之前直接 clean_content[:50]，图片 URL 被截成半截 /uploads/2026/... 路径，
    前端正则（要求以扩展名结尾）匹配不上，列表里就露出了一串存储路径。
    """
    import re as _re
    c = (content or "").strip()
    if not c:
        return ""
    pattern = r"(?:https?://[^\s/]+)?/uploads/[\w/.\-]+\.(?:webp|gif|png|jpe?g)(?:\?[^\s]*)?"

    def _label(m):
        return "[表情]" if "/stickers/" in m.group(0) else "[图片]"

    c = _re.sub(pattern, _label, c, flags=_re.I)
    # 兜底：即便 URL 被更早的逻辑截断过，也不把存储路径露出去
    if "/uploads/" in c:
        c = _re.sub(r"(?:https?://[^\s/]+)?/uploads/\S*", _label, c, flags=_re.I)
    return c[:limit]


def format_utc_iso(dt: Optional[datetime]) -> str:
    """确保输出包含明确的 UTC 时区标识 (Z 或 +00:00)，彻底解决前后端 8 小时时差"""
    if dt is None:
        return ""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.isoformat()


def _as_utc(dt: Any) -> Optional[datetime]:
    """将数据库可能返回的各种时区格式（带/不带时区、ISO字符串）统一转为带 UTC 时区对象，避免比较时抛 TypeError"""
    if dt is None:
        return None
    if isinstance(dt, str):
        try:
            dt = datetime.fromisoformat(dt.replace("Z", "+00:00"))
        except Exception:
            try:
                dt = datetime.strptime(dt, "%Y-%m-%d %H:%M:%S.%f")
            except Exception:
                try:
                    dt = datetime.strptime(dt, "%Y-%m-%d %H:%M:%S")
                except Exception:
                    return None
    if hasattr(dt, "tzinfo"):
        return dt if dt.tzinfo is not None else dt.replace(tzinfo=timezone.utc)
    return None


_seq_lock = asyncio.Lock()
_last_seq = 0

# 本站图片/表情的存储地址（可能带域名前缀）：引用块里必须折叠，绝不外泄路径
_IMAGE_PATH_RE = re.compile(
    r"(?:https?://[^\s/]+)?/uploads/[\w/.\-]+\.(?:webp|gif|png|jpe?g)(?:\?\S*)?",
    re.I,
)


def _reply_snippet_of(content: str) -> str:
    """引用块内容快照：只保留文字，图片/表情地址折叠成占位词。

    直接把 `/uploads/xxx.webp` 存进快照，会在引用块里把存储路径暴露给所有人。
    「文字 + 图片」的消息只保留文字，并追加 `[图片]` / `[表情]` 标识。
    """
    c = (content or "").strip()
    if not c:
        return ""
    urls = _IMAGE_PATH_RE.findall(c)
    if not urls:
        return c[:120].replace("\n", " ")
    text = _IMAGE_PATH_RE.sub(" ", c).replace("\\n", " ").replace("\n", " ")
    text = re.sub(r"\s+", " ", text).strip()
    labels: List[str] = []
    if any("/uploads/stickers/" not in u for u in urls):
        labels.append("[图片]")
    if any("/uploads/stickers/" in u for u in urls):
        labels.append("[表情]")
    label = " ".join(labels)
    return (f"{text} {label}" if text else label)[:120]


async def _group_reply_snapshot(db, room_id, reply_id):
    """按群消息 id 生成引用快照，返回 (reply_to_id, reply_sender, reply_snippet)。

    存快照而不是只存 id：原消息被撤回后，引用块仍然能显示"谁说了什么"，
    否则会出现一排空引用。跨群引用一律丢弃（返回空），防止引用串群。
    """
    if not reply_id:
        return None, "", ""
    row = (await db.execute(
        select(GroupMessage, User)
        .join(User, User.id == GroupMessage.sender_id)
        .where(GroupMessage.id == reply_id, GroupMessage.room_id == room_id)
    )).first()
    if not row:
        return None, "", ""
    origin, sender = row
    return origin.id, (sender.nickname or sender.username), _reply_snippet_of(origin.content)


async def _direct_reply_snapshot(db, conversation_id, reply_id):
    if not reply_id:
        return None, "", ""
    row = (await db.execute(
        select(Message, User)
        .join(User, User.id == Message.sender_id)
        .where(Message.id == reply_id, Message.conversation_id == conversation_id)
    )).first()
    if not row:
        return None, "", ""
    origin, sender = row
    return origin.id, (sender.nickname or sender.username), _reply_snippet_of(origin.content)


# 每个群：1 位群主 + 最多 10 位管理员
GROUP_ADMIN_LIMIT = 10

STICKER_RATE_LIMIT = 5          # 每分钟最多 5 个自定义表情
STICKER_RATE_WINDOW = 60        # 秒

# @全体成员：仅群主/管理员可用，每人每天 10 次（按北京时间自然日结算）
MENTION_ALL_TOKEN = "@全体成员"
MENTION_ALL_DAILY_LIMIT = 10
_BEIJING_TZ = timezone(timedelta(hours=8))


# ---- 群聊独立等级 / 经验 / 铭牌 ----
GROUP_LEVEL_MAX = 20
GROUP_LEVEL_STEP = 280          # 线性：每 280 EXP 升 1 级，满级需 5320 EXP（约 67 天活跃）
GROUP_DAILY_MSG_CAP = 20        # 每日计分发言条数
GROUP_DAILY_EXP_CAP = 80        # 每日经验上限（满级约需 67 天持续活跃）
GROUP_BADGE_NAMES = [
    "刚来蹭网", "潜水观光", "偶尔冒泡", "水群新兵", "稳定出没",
    "话题搭子", "吃瓜选手", "消息机器", "群聊常客", "活跃选手",
    "话题发动机", "楼王预备役", "抬杠大师", "爆料观察员", "气氛支柱",
    "常驻原住民", "群里老面孔", "传说路人", "群内传说", "次元锚点",
]


def group_level_of(exp: int) -> int:
    return max(1, min(GROUP_LEVEL_MAX, 1 + int(exp or 0) // GROUP_LEVEL_STEP))


def group_badge_of(member: Optional[GroupMember]) -> tuple:
    """返回 (配色用的等级 id, 展示文字)。

    规则（v2）：
    - 等级铭牌只由 group_exp 推出的 group_level 决定，任何管理员都不能指定；
    - 佩戴了已授予的特殊名牌时，文字用特殊名牌名，配色仍按当前群等级。
    """
    if not member:
        return 1, GROUP_BADGE_NAMES[0]
    lvl = group_level_of(getattr(member, "group_exp", 0))
    special = (getattr(member, "special_badge_name", None) or "").strip()
    if special:
        return lvl, special[:32]
    return lvl, GROUP_BADGE_NAMES[lvl - 1]


def _group_today() -> date:
    return datetime.now(_BEIJING_TZ).date()


async def award_group_exp(db: AsyncSession, member: Optional[GroupMember], content: str = "") -> None:
    """群聊经验：每日发言条数 + 互动次数(@) + 互动热度(长内容)，线性累积但每日封顶。"""
    if not member:
        return
    today = _group_today()
    if getattr(member, "today_exp_date", None) != today:
        member.today_exp_date = today
        member.today_msg_count = 0
        member.today_exp_gained = 0

    gain = 0
    # 1) 每日发言条数（前 20 条每条 2 EXP）
    if (getattr(member, "today_msg_count", 0) or 0) < GROUP_DAILY_MSG_CAP:
        member.today_msg_count = (getattr(member, "today_msg_count", 0) or 0) + 1
        gain += 2
    # 2) 互动次数（@ 他人）
    if "@" in (content or ""):
        member.interaction_count = (getattr(member, "interaction_count", 0) or 0) + 1
        gain += 1
    # 3) 互动热度（长内容 / 群内热闹程度）
    heat = min(3, len(content or "") // 50)
    if heat:
        member.heat_score = (getattr(member, "heat_score", 0) or 0) + heat
        gain += heat

    left = max(0, GROUP_DAILY_EXP_CAP - (getattr(member, "today_exp_gained", 0) or 0))
    gain = min(gain, left)
    if gain <= 0:
        return
    member.today_exp_gained = (getattr(member, "today_exp_gained", 0) or 0) + gain
    member.group_exp = (getattr(member, "group_exp", 0) or 0) + gain
    member.group_level = group_level_of(member.group_exp)


async def maybe_group_checkin(db: AsyncSession, room: GroupRoom,
                              member: Optional[GroupMember], content: str) -> tuple:
    """命中群签到语时记账并额外加经验，返回 (msg_type, 本次经验, 第几次)。
    按业务时区自然日 + occurrence 控制每日 1/3 次；未命中返回普通消息。"""
    text = (getattr(room, "checkin_text", "") or "").strip()
    if not text or not member or (content or "").strip() != text:
        return "text", 0, 0
    today = _group_today()
    used = int((await db.scalar(
        select(func.count()).select_from(GroupCheckin).where(
            GroupCheckin.room_id == room.id,
            GroupCheckin.user_id == member.user_id,
            GroupCheckin.checkin_date == today,
        )
    )) or 0)
    # 每日次数由超管全局配置（1-3），单次经验固定 10，均不在群内单独设置
    from app.services.level_service import get_system_setting_int
    limit = max(1, min(3, await get_system_setting_int(db, "checkin_times_per_day", 1)))
    if used >= limit:
        return "text", 0, used
    bonus = 10
    db.add(GroupCheckin(
        id=uuid4(), room_id=room.id, user_id=member.user_id,
        checkin_date=today, occurrence=used + 1, exp=bonus,
    ))
    member.group_exp = (getattr(member, "group_exp", 0) or 0) + bonus
    member.group_level = group_level_of(member.group_exp)
    return "checkin", bonus, used + 1


async def _check_group_mute(room: GroupRoom, user: User, member: Optional[GroupMember]) -> Optional[str]:
    """HTTP / WS 双通道共用的禁言校验；群主/管理员/站长豁免，过期自动解除。"""
    if not member or not getattr(member, "muted_until", None):
        return None
    until = _as_utc(member.muted_until)
    if until and until > datetime.now(timezone.utc):
        if _is_group_manager(room, user, member) or _is_site_admin(user):
            return None
        return f"你已被禁言，至 {until.astimezone(_BEIJING_TZ).strftime('%m-%d %H:%M')} 解除"
    member.muted_until = None
    member.mute_reason = None
    return None


def _is_group_manager(room: GroupRoom, user: User, member: Optional[GroupMember]) -> bool:
    """群主 / 群管理员 / 站内管理员 拥有公告管理权限。"""
    if _is_site_admin(user):
        return True
    if room.creator_id == user.id:
        return True
    return bool(member and member.role in ("owner", "admin"))


async def _mention_all_used_today(db: AsyncSession, user_id) -> int:
    now_local = datetime.now(_BEIJING_TZ)
    day_start = now_local.replace(hour=0, minute=0, second=0, microsecond=0).astimezone(timezone.utc)
    return int(
        (await db.execute(
            select(func.count(GroupMessage.id)).where(
                GroupMessage.sender_id == user_id,
                GroupMessage.is_mention_all.is_(True),
                GroupMessage.created_at >= day_start,
            )
        )).scalar() or 0
    )


async def _check_mention_all(
    db: AsyncSession,
    room: GroupRoom,
    user: User,
    member: Optional[GroupMember],
    content: str,
):
    """校验 @全体成员 权限与当日额度。

    返回 (是否 @全体成员, 错误码, 错误说明)；HTTP 与 WebSocket 两条通道共用。
    """
    if MENTION_ALL_TOKEN not in (content or ""):
        return False, None, None
    if not _is_group_manager(room, user, member):
        return False, 403, "只有群主或管理员可以使用 @全体成员"
    used = await _mention_all_used_today(db, user.id)
    if used >= MENTION_ALL_DAILY_LIMIT:
        return False, 429, f"今天的 @全体成员 已用 {used}/{MENTION_ALL_DAILY_LIMIT} 次，明天再来吧"
    return True, None, None


async def notify_group_mention_all(db: AsyncSession, room: GroupRoom, sender: User, msg_id, sender_name: str):
    """@全体成员：给群内每个成员单独推一帧，未打开该群也能收到提醒。"""
    try:
        rows = (await db.execute(
            select(GroupMember.user_id).where(GroupMember.room_id == room.id)
        )).scalars().all()
    except Exception:
        return
    payload = {
        "type": "mention_all",
        "room_id": str(room.id),
        "room_name": room.name,
        "msg_id": str(msg_id),
        "sender": sender_name,
    }
    for uid in rows:
        if str(uid) == str(sender.id):
            continue
        try:
            await ws_manager.send_personal_message(payload, str(uid))
        except Exception:
            pass


async def _count_group_admins(db, room_id) -> int:
    """统计群内管理员数量（不含群主）。"""
    return int(
        (await db.execute(
            select(func.count(GroupMember.id)).where(
                GroupMember.room_id == room_id,
                GroupMember.role == "admin",
            )
        )).scalar() or 0
    )


async def _sticker_rate_ok(user_id) -> bool:
    """自定义表情包发送频率限制：防止有人用表情刷屏。"""
    try:
        from app.core.database import get_redis

        redis = get_redis()
        if not redis:
            return True
        import time as _time

        # 滑动窗口：固定窗口在临界点前后 2 秒内可以发两倍额度。
        # 用 ZSET 记录每次发送时间，只统计最近 STICKER_RATE_WINDOW 秒。
        now = _time.time()
        key = f"stickerrl:z:{user_id}"
        pipe = redis.pipeline()
        pipe.zremrangebyscore(key, 0, now - STICKER_RATE_WINDOW)
        pipe.zadd(key, {f"{now:.6f}": now})
        pipe.zcard(key)
        pipe.expire(key, STICKER_RATE_WINDOW + 5)
        results = await pipe.execute()
        used = int(results[2] or 0)
        return used <= STICKER_RATE_LIMIT
    except Exception:
        return True


async def next_seq_id() -> int:
    """
    生成进程内单调递增的消息序列号。

    messages.seq_id / group_messages.seq_id 均非主键，数据库不会自动赋值，
    因此统一由应用层生成；取「微秒时间戳」与「上一次值 +1」的较大者，
    既保证单调性（稳定排序/去重），又保证重启后不会回退。
    当前部署为单 worker (见 Dockerfile)，不涉及多进程冲突。
    """
    global _last_seq
    async with _seq_lock:
        seq = max(_last_seq + 1, int(time.time() * 1_000_000))
        _last_seq = seq
        return seq


def _is_site_admin(user: User) -> bool:
    return user.role in ("super_admin", "admin")


def _not_deleted_for(conv_model, user_id: UUID):
    """会话过滤条件：只返回「未被当前用户软删除」的会话"""
    return or_(
        and_(conv_model.user_small_id == user_id, conv_model.user_small_deleted_at.is_(None)),
        and_(conv_model.user_big_id == user_id, conv_model.user_big_deleted_at.is_(None)),
    )


def _deleted_flag_for(conv: Conversation, user_id: UUID):
    """返回当前用户在该会话上的软删除字段名"""
    return "user_small_deleted_at" if conv.user_small_id == user_id else "user_big_deleted_at"


def _cleared_flag_for(conv: Conversation, user_id: UUID):
    """返回当前用户在该会话上的"已清空历史"字段名"""
    return "user_small_cleared_at" if conv.user_small_id == user_id else "user_big_cleared_at"


def _message_visible_for(conv_model, user_id: UUID):
    """消息可见性条件：只返回当前用户"清空聊天记录"之后产生的消息。

    从未清空过时条件恒真；需要与 Conversation 表 join 后使用。
    """
    return or_(
        and_(
            conv_model.user_small_id == user_id,
            or_(
                conv_model.user_small_cleared_at.is_(None),
                Message.created_at > conv_model.user_small_cleared_at
            )
        ),
        and_(
            conv_model.user_big_id == user_id,
            or_(
                conv_model.user_big_cleared_at.is_(None),
                Message.created_at > conv_model.user_big_cleared_at
            )
        ),
    )


async def get_group_member(db: AsyncSession, room_id: UUID, user_id: UUID) -> Optional[GroupMember]:
    member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user_id)
    )).scalar_one_or_none()
    if not member:
        # 兜底校验：如果是群创建者，只读自愈识别群主成员，杜绝群主发言报无权限
        room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
        if room and room.creator_id == user_id:
            # 历史群缺成员行：就地自愈落库，避免后续给游离对象赋 last_read_at（永不生效）
            healed = GroupMember(room_id=room_id, user_id=user_id, role="owner")
            db.add(healed)
            try:
                await db.flush()
                return healed
            except Exception:
                await db.rollback()
                return (await db.execute(
                    select(GroupMember).where(
                        GroupMember.room_id == room_id, GroupMember.user_id == user_id
                    )
                )).scalar_one_or_none()
    return member


async def get_group_member_ids(db: AsyncSession, room_id: UUID) -> List[str]:
    rows = (await db.execute(select(GroupMember.user_id).where(GroupMember.room_id == room_id))).scalars().all()
    creator_res = (await db.execute(select(GroupRoom.creator_id).where(GroupRoom.id == room_id))).scalar_one_or_none()
    uids = set(str(uid) for uid in rows)
    if creator_res:
        uids.add(str(creator_res))
    return list(uids)


async def push_group_event(db: AsyncSession, room_id: UUID, is_public: bool, payload: dict, extra_target_id: Optional[str] = None):
    """群聊事件定向推送：群成员 ∪（公开群）正在浏览该频道的订阅者 ∪ 额外接收方。

    私密群必须以「当前成员」为准：订阅集合是内存历史态，成员被踢/退群/群解散后
    若仍按订阅推送，已无权的人会继续收到消息正文。公开群内容本就公开，仍保留订阅者。
    """
    targets = set(await get_group_member_ids(db, room_id))
    if is_public:
        targets |= ws_manager.room_subscribers(str(room_id))
    if extra_target_id:
        targets.add(str(extra_target_id))
    await ws_manager.send_to_users(payload, targets)


async def log_group_penetration(
    db: AsyncSession,
    room: GroupRoom,
    operator: User,
    action: str,
    target_user_id: Optional[UUID] = None,
    detail: str = "",
    client_ip: str = ""
) -> bool:
    """记录站长对「自己并未加入的私密群」的穿透操作。

    普通群内管理动作不落这条日志（避免噪音），只有管理员绕过成员身份直接读写
    他人私密群数据时才留痕，用于事后追溯。返回是否真的写入了审计记录。
    """
    if room.is_public or not _is_site_admin(operator):
        return False
    if await get_group_member(db, room.id, operator.id):
        return False

    db.add(GroupAuditLog(
        room_id=room.id,
        room_name=room.name or "",
        operator_id=operator.id,
        action=action,
        target_user_id=target_user_id,
        detail=(detail or "")[:255],
        client_ip=(client_ip or "")[:64]
    ))
    return True


async def notify_group_members_changed(db: AsyncSession, room_id: UUID, is_public: bool, reason: str):
    """群成员数量/构成发生变化时通知客户端刷新（用于成员数、成员名单同步）"""
    await push_group_event(db, room_id, is_public, {
        "type": "group_members_changed",
        "room_id": str(room_id),
        "reason": reason
    })


@router.get("/unread-count")
async def get_total_unread_count(
    muted: str = Query("", max_length=2000),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """获取当前用户私聊 + 群聊总未读消息数 (用于导航栏红点提示)。

    `muted` 是前端本地保存的免打扰会话/群 id（逗号分隔）；
    这些会话的未读不计入红点，避免「静音了照样亮红点」。
    """
    muted_ids = []
    for raw in (muted or "").split(","):
        raw = raw.strip()
        if not raw:
            continue
        try:
            muted_ids.append(UUID(raw))
        except (ValueError, TypeError):
            continue

    query = (
        select(func.count(Message.id))
        .join(Conversation, Message.conversation_id == Conversation.id)
        .where(
            _not_deleted_for(Conversation, user.id),
            _message_visible_for(Conversation, user.id),
            Message.sender_id != user.id,
            Message.is_read == False,
            ~Message.conversation_id.in_(muted_ids) if muted_ids else true(),
        )
    )
    private_cnt = (await db.execute(query)).scalar() or 0

    # 群聊未读：只统计加入(或上次已读)之后、非本人发送的消息
    group_cnt = (await db.execute(
        select(func.count(GroupMessage.id))
        .join(GroupMember, and_(
            GroupMember.room_id == GroupMessage.room_id,
            GroupMember.user_id == user.id
        ))
        .where(
            GroupMessage.sender_id != user.id,
            GroupMessage.created_at > func.coalesce(GroupMember.last_read_at, GroupMember.joined_at),
            ~GroupMessage.room_id.in_(muted_ids) if muted_ids else true(),
        )
    )).scalar() or 0

    return {"unread_count": private_cnt + group_cnt}

@router.get("/conversations", response_model=List[ConversationResponse])
async def list_conversations(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    query = select(Conversation).where(
        _not_deleted_for(Conversation, user.id)
    ).order_by(Conversation.last_message_at.desc())
    res = await db.execute(query)
    conversations = res.scalars().all()

    # 获取当前用户的拉黑与被拉黑关系
    my_blocks_q = select(UserBlock.blocked_id).where(UserBlock.blocker_id == user.id)
    my_blocked_ids = set((await db.execute(my_blocks_q)).scalars().all())

    blocked_me_q = select(UserBlock.blocker_id).where(UserBlock.blocked_id == user.id)
    blocked_me_ids = set((await db.execute(blocked_me_q)).scalars().all())

    # 统计每个会话的未读消息数
    conv_ids = [c.id for c in conversations]
    unread_counts_map = {}
    if conv_ids:
        unread_q = (
            select(Message.conversation_id, func.count(Message.id))
            .where(
                Message.conversation_id.in_(conv_ids),
                Message.sender_id != user.id,
                Message.is_read == False
            )
            .group_by(Message.conversation_id)
        )
        unread_res = await db.execute(unread_q)
        for cid, cnt in unread_res.all():
            unread_counts_map[cid] = cnt

    # 批量查询对端用户资料，避免逐个会话查询造成 N+1
    peer_ids = list({
        conv.user_big_id if conv.user_small_id == user.id else conv.user_small_id
        for conv in conversations
    })
    peers_map = {}
    if peer_ids:
        peer_rows = (await db.execute(select(User).where(User.id.in_(peer_ids)))).scalars().all()
        peers_map = {p.id: p for p in peer_rows}

    result = []
    for conv in conversations:
        peer_id = conv.user_big_id if conv.user_small_id == user.id else conv.user_small_id
        peer = peers_map.get(peer_id)

        # 本人清空过聊天记录且清空后再无新消息时，列表不应再展示旧消息预览
        my_cleared = _as_utc(getattr(conv, _cleared_flag_for(conv, user.id)))
        last_at = _as_utc(conv.last_message_at)
        preview_hidden = bool(my_cleared and last_at and last_at <= my_cleared)

        result.append(ConversationResponse(
            id=conv.id,
            peer_user_id=peer_id,
            peer_username=peer.username if peer else "未知用户",
            peer_nickname=peer.nickname if peer else None,
            peer_avatar=peer.avatar_url if peer else None,
            last_message_preview="" if preview_hidden else (conv.last_message_preview or ""),
            last_message_at=conv.last_message_at,
            unread_count=unread_counts_map.get(conv.id, 0),
            is_blocked=peer_id in my_blocked_ids,
            is_blocked_by_peer=peer_id in blocked_me_ids
        ))
    return result

@router.post("/conversations")
async def get_or_create_conversation(
    target_user_id: UUID,
    user: User = Depends(get_current_active_user), # 见习会员不可主动发起
    db: AsyncSession = Depends(get_db)
):
    if target_user_id == user.id:
        raise HTTPException(status_code=400, detail="不可与自己建立私聊")

    # 1. 检查黑名单关系拦截
    block_check = await db.execute(
        select(UserBlock).where(
            or_(
                and_(UserBlock.blocker_id == user.id, UserBlock.blocked_id == target_user_id),
                and_(UserBlock.blocker_id == target_user_id, UserBlock.blocked_id == user.id)
            )
        )
    )
    if block_check.scalar_one_or_none():
        raise HTTPException(status_code=403, detail="由于黑名单限制或隐私防护，无法与该用户发起私聊")

    # 2. 检查目标用户是否存在及其免打扰模式
    t_query = select(User).where(User.id == target_user_id)
    target_user = (await db.execute(t_query)).scalar_one_or_none()
    if not target_user:
        raise HTTPException(status_code=404, detail="目标用户不存在")

    # 封禁/注销用户不可作为私信接收方（对方无法回应但通知照发的单面骚扰面）
    if target_user.status in ("banned", "deactivated", "deleting"):
        raise HTTPException(status_code=403, detail="该账号状态异常，无法发起私聊")

    if target_user.dnd_enabled and user.role not in ["super_admin", "admin"]:
        f_check = await db.execute(
            select(Follow).where(Follow.follower_id == target_user_id, Follow.following_id == user.id)
        )
        if not f_check.scalar_one_or_none():
            raise HTTPException(status_code=403, detail="对方已开启免打扰模式，仅接收已关注极客的私信")

    u_small = min(user.id, target_user_id)
    u_big = max(user.id, target_user_id)

    query = select(Conversation).where(Conversation.user_small_id == u_small, Conversation.user_big_id == u_big)
    conv = (await db.execute(query)).scalar_one_or_none()

    if not conv:
        conv = Conversation(user_small_id=u_small, user_big_id=u_big)
        db.add(conv)
        try:
            await db.commit()
        except IntegrityError:
            # 并发同时创建同一对会话时，唯一约束会拒绝后到的插入，此处回滚并复用已有会话
            await db.rollback()
            conv = (await db.execute(query)).scalar_one_or_none()
            if not conv:
                raise HTTPException(status_code=500, detail="会话创建失败，请重试")
    else:
        # 主动发起会话即恢复双方视图（任一方此前删除过该会话都能重新看到）
        if conv.user_small_deleted_at or conv.user_big_deleted_at:
            conv.user_small_deleted_at = None
            conv.user_big_deleted_at = None
            await db.commit()

    return {"conversation_id": conv.id}

@router.get("/{conv_id}/messages", response_model=List[MessageResponse])
async def get_messages(
    conv_id: UUID,
    page: int = Query(1, ge=1),
    size: int = Query(30, ge=1, le=100),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    conv_q = select(Conversation).where(Conversation.id == conv_id)
    conv = (await db.execute(conv_q)).scalar_one_or_none()
    if not conv or (conv.user_small_id != user.id and conv.user_big_id != user.id):
        raise HTTPException(status_code=403, detail="无权查看该会话")

    # 已被本人删除的会话不再返回历史（对方的历史不受影响）
    if getattr(conv, _deleted_flag_for(conv, user.id)):
        return []

    offset = (page - 1) * size
    # created_at + seq_id 双重排序，避免同一时刻批量写入时的分页抖动/重复
    m_query = select(Message).where(Message.conversation_id == conv_id)
    # 本人执行过"清空聊天记录"时，只返回清空时间点之后的消息（对方记录不受影响）
    my_cleared_at = getattr(conv, _cleared_flag_for(conv, user.id))
    if my_cleared_at:
        m_query = m_query.where(Message.created_at > my_cleared_at)
    m_query = m_query.order_by(
        Message.created_at.desc(), Message.seq_id.desc().nulls_last()
    ).offset(offset).limit(size)
    res = await db.execute(m_query)
    messages = res.scalars().all()
    # 按时间正序返回给前端渲染
    messages.reverse()

    # 自动将对方发来的未读消息标记为已读
    mark_stmt = (
        update(Message)
        .where(Message.conversation_id == conv_id, Message.sender_id != user.id, Message.is_read == False)
        .values(is_read=True)
    )
    await db.execute(mark_stmt)

    # 同步将对应的私信通知标记为已读
    notif_stmt = (
        update(Notification)
        .where(
            Notification.receiver_id == user.id,
            Notification.target_id == conv_id,
            Notification.action_type == "chat_message",
            Notification.is_read == False
        )
        .values(is_read=True)
    )
    await db.execute(notif_stmt)
    await db.commit()

    return [
        MessageResponse(
            id=m.id,
            conversation_id=m.conversation_id,
            sender_id=m.sender_id,
            msg_type=m.msg_type,
            content="[该消息已撤回]" if m.is_recalled else m.content,
            seq_id=m.seq_id,
            is_read=m.is_read,
            is_recalled=m.is_recalled,
            created_at=m.created_at,
            reply_to_id=m.reply_to_id,
            reply_sender=m.reply_sender,
            # 历史快照里可能存着图片存储路径：读取时统一清洗
            reply_snippet=_reply_snippet_of(m.reply_snippet or ''),
        ) for m in messages
    ]

@router.post("/conversations/{conv_id}/read")
async def mark_conversation_read(
    conv_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """即时将指定私聊会话中对方发送的未读消息标记为已读"""
    # 必须校验会话归属，否则任何人可凭会话 ID 擦除他人的未读状态
    conv = (await db.execute(select(Conversation).where(Conversation.id == conv_id))).scalar_one_or_none()
    if not conv or (conv.user_small_id != user.id and conv.user_big_id != user.id):
        raise HTTPException(status_code=403, detail="无权操作该会话")

    await db.execute(
        update(Message)
        .where(Message.conversation_id == conv_id, Message.sender_id != user.id, Message.is_read == False)
        .values(is_read=True)
    )
    await db.execute(
        update(Notification)
        .where(
            Notification.receiver_id == user.id,
            Notification.target_id == conv_id,
            Notification.action_type == "chat_message",
            Notification.is_read == False
        )
        .values(is_read=True)
    )
    await db.commit()

    # 已读回执已下线：不再向对方广播「我读了」。
    # is_read 仅保留为未读计数的内部水位，接口本身仍负责清红点，不能删除。
    return {"success": True}

@router.post("/messages/{msg_id}/recall")
async def recall_message(
    msg_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """限时 2 分钟内消息撤回"""
    query = select(Message).options(selectinload(Message.conversation)).where(Message.id == msg_id)
    msg = (await db.execute(query)).scalar_one_or_none()
    if not msg:
        raise HTTPException(status_code=404, detail="消息不存在")

    if msg.sender_id != user.id and not _is_site_admin(user):
        raise HTTPException(status_code=403, detail="只能撤回自己发送的消息")

    now = datetime.now(timezone.utc)
    created_at = _as_utc(msg.created_at)
    if created_at and now - created_at > timedelta(minutes=2):
        raise HTTPException(status_code=400, detail="发送已超过 2 分钟，无法撤回")

    msg.is_recalled = True
    conv = msg.conversation
    # 同步更新会话最后一条预览为撤回提示，确保左侧会话列表依然可见且清楚展示状态
    if conv:
        conv.last_message_preview = "[该消息已撤回]"
    await db.commit()

    # WebSocket 实时广播撤回
    if conv:
        await ws_manager.broadcast_recall(str(conv.user_small_id), str(conv.user_big_id), str(conv.id), str(msg.id))
    return {"message": "撤回成功"}

@router.post("/{conv_id}/messages", response_model=MessageResponse)
async def send_message_http(
    conv_id: UUID,
    req: MessageCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """HTTP 方式发送私信消息 (WebSocket 故障或断开时的无缝降级通道)"""
    conv_q = select(Conversation).where(Conversation.id == conv_id)
    conv = (await db.execute(conv_q)).scalar_one_or_none()
    if not conv or (conv.user_small_id != user.id and conv.user_big_id != user.id):
        raise HTTPException(status_code=403, detail="无权在该会话发送消息")

    content = req.content.strip()
    if not content:
        raise HTTPException(status_code=400, detail="消息内容不能为空")

    receiver_id = conv.user_big_id if conv.user_small_id == user.id else conv.user_small_id

    # 1. 检查黑名单双向拦截
    block_check = await db.execute(
        select(UserBlock).where(
            or_(
                and_(UserBlock.blocker_id == user.id, UserBlock.blocked_id == receiver_id),
                and_(UserBlock.blocker_id == receiver_id, UserBlock.blocked_id == user.id)
            )
        )
    )
    if block_check.scalar_one_or_none():
        raise HTTPException(status_code=403, detail="消息发送被拦截：由于黑名单或隐私保护设置，对方拒收您的私信")

    # 2. 检查免打扰
    rec_user = (await db.execute(select(User).where(User.id == receiver_id))).scalar_one_or_none()
    if rec_user and rec_user.dnd_enabled and user.role not in ["super_admin", "admin"]:
        f_check = await db.execute(
            select(Follow).where(Follow.follower_id == receiver_id, Follow.following_id == user.id)
        )
        if not f_check.scalar_one_or_none():
            raise HTTPException(status_code=403, detail="对方已开启免打扰模式，仅接收其已关注极客的私信")

    # 3. 敏感词过滤
    is_blocked, _, clean_content = sensitive_filter.scan(content)
    if is_blocked:
        raise HTTPException(status_code=400, detail="消息包含违规敏感词汇，发送被拦截")

    # 表情包刷屏保护：每分钟最多 5 个
    if is_sticker_content(clean_content) and not await _sticker_rate_ok(user.id):
        raise HTTPException(status_code=429, detail="表情发得太快啦，稍等几秒再发")

    if is_image_message(clean_content) and not is_sticker_content(clean_content):
        from app.services.image_quota import ImageQuotaExceeded, consume_image_quota

        try:
            await consume_image_quota(user, "direct")
        except ImageQuotaExceeded as exc:
            raise HTTPException(status_code=429, detail=str(exc))

    reply_to_id, reply_sender, reply_snippet = await _direct_reply_snapshot(
        db, conv_id, getattr(req, "reply_to_message_id", None)
    )

    new_msg = Message(
        conversation_id=conv_id,
        sender_id=user.id,
        content=clean_content,
        seq_id=await next_seq_id(),
        reply_to_id=reply_to_id,
        reply_sender=reply_sender,
        reply_snippet=reply_snippet,
    )
    db.add(new_msg)
    conv.last_message_preview = _preview_text(clean_content)
    conv.last_message_at = datetime.now(timezone.utc)
    # 新消息到达后恢复双方视图：任一方此前"删除会话"都应重新浮出
    conv.user_small_deleted_at = None
    conv.user_big_deleted_at = None

    # 写入系统消息通知中心
    notif = Notification(
        receiver_id=receiver_id,
        sender_id=user.id,
        action_type="chat_message",
        target_id=conv_id,
        brief=f"发来私信: {clean_content[:50]}"
    )
    db.add(notif)
    await db.commit()
    await db.refresh(new_msg)

    # 广播给接收方 (若在线)
    try:
        await ws_manager.send_personal_message({
            "type": "new_msg",
            "conversation_id": str(conv_id),
            "msg_id": str(new_msg.id),
            "sender_id": str(user.id),
            "content": clean_content,
            "seq_id": new_msg.seq_id,
            "created_at": format_utc_iso(new_msg.created_at),
            # 与 WS 通道保持一致：否则 WS 断开改用 HTTP 时，接收方引用块是空的
            "reply_to_id": str(reply_to_id) if reply_to_id else None,
            "reply_sender": reply_sender or "",
            "reply_snippet": reply_snippet or "",
        }, str(receiver_id))
    except Exception:
        pass

    return MessageResponse(
        id=new_msg.id,
        conversation_id=new_msg.conversation_id,
        sender_id=new_msg.sender_id,
        msg_type=new_msg.msg_type,
        content=new_msg.content,
        seq_id=new_msg.seq_id,
        is_read=new_msg.is_read,
        is_recalled=new_msg.is_recalled,
        created_at=new_msg.created_at,
        reply_to_id=new_msg.reply_to_id,
        reply_sender=new_msg.reply_sender,
        reply_snippet=new_msg.reply_snippet,
    )

@router.delete("/conversations/{conv_id}")
async def delete_conversation(
    conv_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """删除私聊会话（单方软删除：仅对操作者隐藏，绝不删除对方的聊天记录）"""
    conv_q = select(Conversation).where(Conversation.id == conv_id)
    conv = (await db.execute(conv_q)).scalar_one_or_none()
    if not conv or (conv.user_small_id != user.id and conv.user_big_id != user.id):
        raise HTTPException(status_code=403, detail="无权操作该会话")

    # 只置位当前用户一侧的删除标记；对端会话与全部消息记录保持原样
    setattr(conv, _deleted_flag_for(conv, user.id), datetime.now(timezone.utc))
    await db.commit()
    return {"message": "会话已删除"}

@router.delete("/conversations/{conv_id}/messages")
async def clear_conversation_messages(
    conv_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """清空与某用户的聊天记录（单方清空：只对本人隐藏，不删除对方的聊天记录）"""
    conv_q = select(Conversation).where(Conversation.id == conv_id)
    conv = (await db.execute(conv_q)).scalar_one_or_none()
    if not conv or (conv.user_small_id != user.id and conv.user_big_id != user.id):
        raise HTTPException(status_code=403, detail="无权操作该会话")

    # 记录"本人可见的历史截止点"，而非物理删除消息；对方视图与数据完全不受影响
    setattr(conv, _cleared_flag_for(conv, user.id), datetime.now(timezone.utc))
    await db.commit()
    return {"message": "聊天记录已清空"}

# ==================== 多人群聊频道功能 ====================
@router.get("/groups/quota")
async def get_group_creation_quota(
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """获取当前用户的群聊创建配额与权限信息"""
    is_mod = user.role in ["super_admin", "admin", "moderator"]
    pub_cnt = (await db.execute(
        select(func.count(GroupRoom.id)).where(GroupRoom.creator_id == user.id, GroupRoom.is_public == True)
    )).scalar() or 0
    priv_cnt = (await db.execute(
        select(func.count(GroupRoom.id)).where(GroupRoom.creator_id == user.id, GroupRoom.is_public == False)
    )).scalar() or 0
    return {
        "can_create_public": is_mod,
        "public_created_count": pub_cnt,
        "public_max_limit": 2 if user.role == "moderator" else (99 if is_mod else 0),
        "private_created_count": priv_cnt,
        "private_max_limit": 5,
        "user_role": user.role
    }

@router.get("/groups", response_model=List[GroupRoomResponse])
async def list_group_rooms(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """获取群聊频道列表（包含全网公共频道，以及当前用户加入的私密好友群）"""
    if user.role in ["super_admin", "admin"]:
        query = select(GroupRoom).options(selectinload(GroupRoom.creator)).order_by(GroupRoom.created_at.desc())
    else:
        # 仅查询：1. 公开群聊；2. 当前用户已加入的私密群聊；3. 当前用户创建的私密群聊
        my_rooms_subq = select(GroupMember.room_id).where(GroupMember.user_id == user.id)
        query = (
            select(GroupRoom)
            .options(selectinload(GroupRoom.creator))
            .where(or_(
                GroupRoom.is_public == True,
                GroupRoom.creator_id == user.id,
                GroupRoom.id.in_(my_rooms_subq)
            ))
            .order_by(GroupRoom.is_public.desc(), GroupRoom.created_at.desc())
        )

    res = await db.execute(query)
    rooms = res.scalars().all()
    if not rooms:
        return []

    room_ids = [r.id for r in rooms]

    # ---- 批量聚合，彻底消除逐群 4 次查询的 N+1 问题 ----
    # 1. 成员数
    member_counts = dict((await db.execute(
        select(GroupMember.room_id, func.count(GroupMember.id))
        .where(GroupMember.room_id.in_(room_ids))
        .group_by(GroupMember.room_id)
    )).all())

    # 2. 当前用户的成员关系
    my_members = {
        m.room_id: m for m in (await db.execute(
            select(GroupMember).where(
                GroupMember.room_id.in_(room_ids),
                GroupMember.user_id == user.id
            )
        )).scalars().all()
    }

    # 3. 当前用户待审核的入群申请
    pending_rooms = set((await db.execute(
        select(GroupJoinRequest.room_id).where(
            GroupJoinRequest.room_id.in_(room_ids),
            GroupJoinRequest.user_id == user.id,
            GroupJoinRequest.status == "pending"
        )
    )).scalars().all())

    # 4. 每个群最新一条消息 (窗口函数，带容错保护)
    last_msgs = {}
    try:
        last_msg_sq = (
            select(
                GroupMessage.room_id.label("room_id"),
                GroupMessage.content.label("content"),
                GroupMessage.created_at.label("created_at"),
                GroupMessage.is_recalled.label("is_recalled"),
                func.row_number().over(
                    partition_by=GroupMessage.room_id,
                    order_by=(GroupMessage.created_at.desc(), GroupMessage.seq_id.desc().nulls_last())
                ).label("rn")
            )
            .where(GroupMessage.room_id.in_(room_ids))
            .subquery()
        )
        last_msgs = {
            row.room_id: row
            for row in (await db.execute(select(last_msg_sq).where(last_msg_sq.c.rn == 1))).all()
        }
    except Exception as e:
        print(f"[Chat WARN] 查询群最新消息聚合降级: {e}")

    # 5. 未读数：只统计加入之后(或上次已读之后)产生的、非本人发送的消息 (带容错保护)
    unread_map = {}
    try:
        unread_map = dict((await db.execute(
            select(GroupMessage.room_id, func.count(GroupMessage.id))
            .join(GroupMember, and_(
                GroupMember.room_id == GroupMessage.room_id,
                GroupMember.user_id == user.id
            ))
            .where(
                GroupMessage.room_id.in_(room_ids),
                GroupMessage.sender_id != user.id,
                GroupMessage.created_at > func.coalesce(GroupMember.last_read_at, GroupMember.joined_at)
            )
            .group_by(GroupMessage.room_id)
        )).all())
    except Exception as e:
        print(f"[Chat WARN] 查询群聊未读数聚合降级: {e}")

    result = []
    for r in rooms:
        my_m = my_members.get(r.id)
        # 历史群聊缺少成员记录时，以创建者身份兜底展示，避免误判为「未加入」；
        # 这里只做只读兜底，不在 GET 请求中写库（避免并发唯一键冲突）。
        is_creator = bool(r.creator_id and r.creator_id == user.id)
        is_member = my_m is not None or is_creator
        my_role = my_m.role if my_m else ("owner" if is_creator else None)
        mem_cnt = member_counts.get(r.id, 0)
        if mem_cnt == 0 and is_creator:
            mem_cnt = 1

        last_m = last_msgs.get(r.id)
        is_rec = getattr(last_m, "is_recalled", False) if last_m else False
        last_c = getattr(last_m, "content", "") if last_m else ""
        last_at = getattr(last_m, "created_at", None) if last_m else None

        result.append(GroupRoomResponse(
            id=r.id,
            name=r.name,
            description=r.description or "",
            avatar_url=r.avatar_url or f"https://api.dicebear.com/7.x/identicon/svg?seed={r.name}",
            creator_id=r.creator_id,
            creator_username=r.creator.username if r.creator else "官方",
            is_public=r.is_public,
            announcement=r.announcement or "",
            announcement_updated_at=r.announcement_updated_at,
            member_count=mem_cnt,
            is_member=is_member,
            my_role=my_role,
            has_pending_apply=(r.id in pending_rooms) and not is_member,
            unread_count=unread_map.get(r.id, 0) if is_member else 0,
            last_message_preview=("[该消息已撤回]" if is_rec else _preview_text(last_c)),
            last_message_at=last_at if last_at else r.created_at,
            created_at=r.created_at
        ))

    # 按最后活跃时间倒序：有新消息的群自动上浮（与私聊会话列表保持一致）
    def _activity_ts(item: GroupRoomResponse) -> float:
        try:
            dt = _as_utc(item.last_message_at) or _as_utc(item.created_at)
            return dt.timestamp() if dt else 0.0
        except Exception:
            return 0.0

    result.sort(key=_activity_ts, reverse=True)
    return result

@router.post("/groups", response_model=GroupRoomResponse)
async def create_group_room(
    req: GroupRoomCreateRequest,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """
    创建新群聊频道：
    1. 公共频道 (is_public=True): 仅限板块版主 (moderator) 及以上创建，且每位版主最多创建 2 个；
    2. 私密群聊 (is_public=False): 所有正式会员均可创建好友私密群，每人最多可创建 5 个。
    """
    name = req.name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="群聊名称不能为空")

    # 串行化同一用户的建群配额校验：并发请求不再同时通过 COUNT 检查而超额建群
    # (PostgreSQL 下为行级锁，SQLite 会忽略该子句，由单写者模型天然串行)
    await db.execute(select(User.id).where(User.id == user.id).with_for_update())

    is_public = bool(req.is_public)

    # 权限与配额严格限制
    if is_public:
        if user.role not in ["super_admin", "admin", "moderator"]:
            raise HTTPException(
                status_code=403,
                detail="公共群聊为社区开放公共资源，仅限板块版主 (Moderator) 及以上创建。您可以创建私密好友群聊。"
            )
        if user.role == "moderator":
            cnt_res = await db.execute(
                select(func.count(GroupRoom.id)).where(
                    GroupRoom.creator_id == user.id,
                    GroupRoom.is_public == True
                )
            )
            created_cnt = cnt_res.scalar() or 0
            if created_cnt >= 2:
                raise HTTPException(
                    status_code=400,
                    detail="配额超限：每位版主最多允许创建 2 个公共群聊频道，您已达到上限。"
                )
    else:
        # 私密群聊
        if user.role not in ["super_admin", "admin"]:
            cnt_res = await db.execute(
                select(func.count(GroupRoom.id)).where(
                    GroupRoom.creator_id == user.id,
                    GroupRoom.is_public == False
                )
            )
            created_cnt = cnt_res.scalar() or 0
            if created_cnt >= 5:
                raise HTTPException(
                    status_code=400,
                    detail="您创建的私密好友群已达上限 (最多 5 个)，可解散闲置群聊后再新建。"
                )

    avatar = f"https://api.dicebear.com/7.x/identicon/svg?seed={name}"
    room = GroupRoom(
        name=name,
        description=req.description.strip() if req.description else "",
        avatar_url=avatar,
        creator_id=user.id,
        is_public=is_public,
        announcement="",
        announcement_updated_at=None
    )
    db.add(room)
    await db.flush()

    # 将创建者设为群主
    owner_member = GroupMember(
        room_id=room.id,
        user_id=user.id,
        role="owner"
    )
    db.add(owner_member)
    await db.commit()
    await db.refresh(room)

    return GroupRoomResponse(
        id=room.id,
        name=room.name,
        description=room.description or "",
        avatar_url=room.avatar_url,
        creator_id=user.id,
        creator_username=user.username,
        is_public=room.is_public,
        announcement="",
        announcement_updated_at=None,
        member_count=1,
        is_member=True,
        my_role="owner",
        has_pending_apply=False,
        last_message_preview="",
        last_message_at=room.created_at,
        created_at=room.created_at
    )

@router.put("/groups/{room_id}", response_model=GroupRoomResponse)
async def update_group_info(
    room_id: UUID,
    req: GroupRoomUpdateRequest,
    request: Request = None,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """
    修改群聊信息与群公告：
    群主、管理员或超级管理员可修改群名称、描述以及群公告。
    """
    room_res = await db.execute(
        select(GroupRoom).options(selectinload(GroupRoom.creator)).where(GroupRoom.id == room_id)
    )
    room = room_res.scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    is_super = user.role in ["super_admin", "admin"]
    my_member_res = await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )
    my_m = my_member_res.scalar_one_or_none()

    if not is_super and (not my_m or my_m.role not in ["owner", "admin"]):
        raise HTTPException(status_code=403, detail="仅群主或群管理员有权修改群聊信息与群公告")

    # 站长/管理员修改自己未加入的群资料属于穿透操作，必须留痕
    if is_super and (not my_m) and not room.is_public:
        if await log_group_penetration(db, room, user, "update_group",
                                       detail=f"name={bool(req.name)},announcement={bool(req.announcement)}",
                                       client_ip=request.client.host if request and request.client else ""):
            await db.commit()

    if req.name is not None:
        name = req.name.strip()
        if len(name) < 2 or len(name) > 50:
            raise HTTPException(status_code=400, detail="群聊名称长度需在 2 到 50 个字符之间")
        room.name = name

    if req.description is not None:
        room.description = req.description.strip()

    # 群公告：编辑框里改了内容就落一条新的历史公告（作者默认已读），
    # 旧字段只作为镜像，真正的置顶/历史由 group_announcements 驱动。
    new_announcement_text = None
    new_announcement_id = None
    if req.announcement is not None:
        text = req.announcement.strip()
        if text != (room.announcement or "").strip():
            now = datetime.now(timezone.utc)
            new_announcement_text = text
            room.announcement = text
            room.announcement_updated_at = now
            if text:
                new_announcement_id = uuid.uuid4()
                db.add(GroupAnnouncement(
                    id=new_announcement_id,
                    room_id=room.id,
                    content=text,
                    created_by=user.id,
                    created_at=now,
                ))
                # flush 保证先有公告行再写已读记录（避免外键顺序问题）
                await db.flush()
                db.add(GroupAnnouncementRead(
                    id=uuid.uuid4(), announcement_id=new_announcement_id, room_id=room.id, user_id=user.id, read_at=now
                ))
            else:
                # 在编辑框里清空公告 = 撤下当前所有公告（软删除，历史留痕）
                await db.execute(
                    update(GroupAnnouncement)
                    .where(
                        GroupAnnouncement.room_id == room.id,
                        GroupAnnouncement.is_deleted.is_(False),
                    )
                    .values(is_deleted=True, deleted_at=now, deleted_by=user.id)
                )

    # 群头像属于群的"门面"，只允许群主（或站长）更换，
    # 普通管理员即使传了也不会生效，避免几个人互相覆盖。
    if req.avatar_url is not None:
        is_owner = bool(my_m and my_m.role == "owner")
        if not (is_owner or is_super):
            raise HTTPException(status_code=403, detail="只有群主可以更换群头像")
        room.avatar_url = req.avatar_url.strip()

    room.updated_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(room)

    # 公告有变更（发布 / 清空）：实时通知群成员刷新置顶卡片与历史列表
    if new_announcement_text is not None:
        await push_group_event(db, room.id, room.is_public, {
            "type": "group_announcement",
            "room_id": str(room.id),
            "announcement_id": str(new_announcement_id) if new_announcement_id else None,
            "content": new_announcement_text,
            "sender_id": str(user.id),
            "sender": user.nickname or user.username,
            "created_at": format_utc_iso(room.announcement_updated_at),
        })

    # 统计成员数
    mem_cnt = (await db.execute(
        select(func.count(GroupMember.id)).where(GroupMember.room_id == room.id)
    )).scalar() or 0

    # 保留真实的最后一条消息，避免编辑群资料后列表预览被清空/时间被刷新
    last_m = (await db.execute(
        select(GroupMessage)
        .where(GroupMessage.room_id == room.id)
        .order_by(GroupMessage.created_at.desc(), GroupMessage.seq_id.desc().nulls_last())
        .limit(1)
    )).scalar_one_or_none()

    return GroupRoomResponse(
        id=room.id,
        name=room.name,
        description=room.description or "",
        avatar_url=room.avatar_url or f"https://api.dicebear.com/7.x/identicon/svg?seed={room.name}",
        creator_id=room.creator_id,
        creator_username=room.creator.username if room.creator else "官方",
        is_public=room.is_public,
        announcement=room.announcement or "",
        announcement_updated_at=room.announcement_updated_at,
        member_count=mem_cnt,
        is_member=my_m is not None or (room.creator_id == user.id),
        my_role=my_m.role if my_m else ("owner" if (is_super or room.creator_id == user.id) else None),
        has_pending_apply=False,
        last_message_preview=("[该消息已撤回]" if last_m and last_m.is_recalled else _preview_text(last_m.content if last_m else "")),
        last_message_at=last_m.created_at if last_m else room.created_at,
        created_at=room.created_at
    )

@router.post("/groups/{room_id}/apply")
async def apply_to_join_group(
    room_id: UUID,
    req: GroupJoinApplyRequest,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """
    提交加入群聊申请：
    进群经过管理员审批同意后方可进群。
    """
    room_res = await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))
    room = room_res.scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    # 检查是否已经是群成员
    existing_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()
    if existing_member:
        raise HTTPException(status_code=400, detail="您已在该群聊中")

    # 检查是否已有待审核申请
    existing_req = (await db.execute(
        select(GroupJoinRequest).where(
            GroupJoinRequest.room_id == room_id,
            GroupJoinRequest.user_id == user.id,
            GroupJoinRequest.status == "pending"
        )
    )).scalar_one_or_none()

    reason_text = req.reason.strip() if req.reason else ""
    if existing_req:
        existing_req.reason = reason_text
        existing_req.updated_at = datetime.now(timezone.utc)
        await db.commit()
        return {"message": "已更新您的入群申请，请耐心等待管理员审核"}

    new_apply = GroupJoinRequest(
        room_id=room_id,
        user_id=user.id,
        reason=reason_text,
        status="pending"
    )
    db.add(new_apply)

    # 查出群主及管理员，发送系统通知
    admin_members = (await db.execute(
        select(GroupMember.user_id).where(
            GroupMember.room_id == room_id,
            GroupMember.role.in_(["owner", "admin"])
        )
    )).scalars().all()

    applicant_name = user.nickname or user.username
    for admin_uid in admin_members:
        if admin_uid != user.id:
            notif = Notification(
                receiver_id=admin_uid,
                sender_id=user.id,
                action_type="group_apply",
                target_id=room_id,
                brief=f"用户「{applicant_name}」申请加入群聊「{room.name}」"
            )
            db.add(notif)

    try:
        await db.commit()
    except IntegrityError:
        # 并发重复提交被部分唯一索引拦下，视为"已在审核中"
        await db.rollback()
        return {"message": "已提交过入群申请，请耐心等待管理员审核"}
    return {"message": "入群申请已提交，请等待管理员审核"}

@router.get("/groups/{room_id}/requests", response_model=List[GroupJoinRequestResponse])
async def list_group_join_requests(
    room_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """
    获取群聊待审批申请列表（群主/管理员专属）
    """
    room_res = await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))
    room = room_res.scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    is_super = user.role in ["super_admin", "admin"]
    my_m = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()

    if not is_super and (not my_m or my_m.role not in ["owner", "admin"]):
        raise HTTPException(status_code=403, detail="仅群主或群管理员有权查看入群申请")

    reqs = (await db.execute(
        select(GroupJoinRequest)
        .options(selectinload(GroupJoinRequest.user))
        .where(GroupJoinRequest.room_id == room_id, GroupJoinRequest.status == "pending")
        .order_by(GroupJoinRequest.created_at.desc())
    )).scalars().all()

    return [
        GroupJoinRequestResponse(
            id=r.id,
            room_id=r.room_id,
            user_id=r.user_id,
            username=r.user.username if r.user else "未知用户",
            nickname=r.user.nickname if r.user else None,
            avatar_url=r.user.avatar_url if r.user else "",
            level=r.user.level if r.user else 1,
            reason=r.reason or "",
            status=r.status,
            created_at=r.created_at
        )
        for r in reqs
    ]

@router.post("/groups/{room_id}/requests/{request_id}/approve")
async def approve_group_join_request(
    room_id: UUID,
    request_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """
    同意入群申请（群主/管理员专属）
    """
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    is_super = user.role in ["super_admin", "admin"]
    my_m = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()

    if not is_super and (not my_m or my_m.role not in ["owner", "admin"]):
        raise HTTPException(status_code=403, detail="仅群主或群管理员有权审批入群申请")

    join_req = (await db.execute(
        select(GroupJoinRequest).where(GroupJoinRequest.id == request_id, GroupJoinRequest.room_id == room_id)
    )).scalar_one_or_none()
    if not join_req:
        raise HTTPException(status_code=404, detail="入群申请记录不存在")

    if join_req.status != "pending":
        raise HTTPException(status_code=400, detail=f"该申请已处理 (当前状态: {join_req.status})")

    join_req.status = "approved"
    join_req.handled_by = user.id
    join_req.updated_at = datetime.now(timezone.utc)

    # 检查是否已是成员，若不是则加入
    already = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == join_req.user_id)
    )).scalar_one_or_none()
    if not already:
        # last_read_at 置为当前时间：入群前的历史消息不计入未读
        db.add(GroupMember(
            room_id=room_id,
            user_id=join_req.user_id,
            role="member",
            last_read_at=datetime.now(timezone.utc)
        ))

    # 给申请人发送通知
    db.add(Notification(
        receiver_id=join_req.user_id,
        sender_id=user.id,
        action_type="group_apply_approved",
        target_id=room_id,
        brief=f"恭喜！您加入群聊「{room.name}」的申请已通过"
    ))

    try:
        await db.commit()
    except IntegrityError:
        # 两名管理员同时点"同意"时，后者会撞成员唯一键，按"已入群"处理即可
        await db.rollback()
        return {"message": "该用户已在群聊中"}

    # 通知申请人刷新群列表，并让群内其他客户端同步成员数/名单
    await ws_manager.send_personal_message({
        "type": "group_joined",
        "room_id": str(room_id),
        "room_name": room.name
    }, str(join_req.user_id))
    await notify_group_members_changed(db, room_id, room.is_public, "member_joined")
    return {"message": "已同意该用户的入群申请，已将其加入群聊"}

@router.post("/groups/{room_id}/requests/{request_id}/reject")
async def reject_group_join_request(
    room_id: UUID,
    request_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """
    拒绝入群申请（群主/管理员专属）
    """
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    is_super = user.role in ["super_admin", "admin"]
    my_m = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()

    if not is_super and (not my_m or my_m.role not in ["owner", "admin"]):
        raise HTTPException(status_code=403, detail="仅群主或群管理员有权审批入群申请")

    join_req = (await db.execute(
        select(GroupJoinRequest).where(GroupJoinRequest.id == request_id, GroupJoinRequest.room_id == room_id)
    )).scalar_one_or_none()
    if not join_req:
        raise HTTPException(status_code=404, detail="入群申请记录不存在")

    if join_req.status != "pending":
        raise HTTPException(status_code=400, detail=f"该申请已处理 (当前状态: {join_req.status})")

    join_req.status = "rejected"
    join_req.handled_by = user.id
    join_req.updated_at = datetime.now(timezone.utc)

    # 给申请人发送通知
    db.add(Notification(
        receiver_id=join_req.user_id,
        sender_id=user.id,
        action_type="group_apply_rejected",
        target_id=room_id,
        brief=f"很遗憾，您加入群聊「{room.name}」的申请已被管理员拒绝"
    ))

    await db.commit()
    return {"message": "已拒绝该用户的入群申请"}

@router.post("/groups/{room_id}/join")
async def join_group_room(
    room_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """主动加入群聊（仅超级管理员可直接加入，普通成员需提交入群申请）"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    existing = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()
    if existing:
        return {"message": "您已在群聊中"}

    if user.role not in ["super_admin", "admin"]:
        raise HTTPException(
            status_code=400,
            detail="进群需经过申请同意后方可加入，请使用申请入群功能"
        )

    new_member = GroupMember(
        room_id=room_id,
        user_id=user.id,
        role="member",
        last_read_at=datetime.now(timezone.utc)
    )
    db.add(new_member)
    await db.commit()

    await ws_manager.send_personal_message({
        "type": "group_joined",
        "room_id": str(room_id),
        "room_name": room.name
    }, str(user.id))
    await notify_group_members_changed(db, room_id, room.is_public, "member_joined")
    return {"message": "成功加入群聊"}

@router.post("/groups/{room_id}/invite")
async def invite_to_group(
    room_id: UUID,
    req: GroupInviteRequest,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """邀请用户加入群聊"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    # 校验发起人必须在群内
    inviter = await get_group_member(db, room_id, user.id)
    is_super = _is_site_admin(user)
    if not inviter and not is_super:
        raise HTTPException(status_code=403, detail="您还不是该群成员，无法邀请他人")

    # 邀请制约束：公开群普通成员需走申请审批，私密群更是邀请+审批制。
    # 此前公开群任意普通成员可直接把任何人拉入群，完全绕过审批状态机。
    if not is_super and (not inviter or inviter.role not in ("owner", "admin")):
        raise HTTPException(status_code=403, detail="仅群主或群管理员可邀请成员，普通成员请让对方提交入群申请")

    # 查找目标用户
    target_username = req.username.strip()
    target_user = (await db.execute(
        select(User).where(User.username == target_username)
    )).scalar_one_or_none()
    if not target_user:
        raise HTTPException(status_code=404, detail=f"未找到用户「{target_username}」")

    if target_user.status == "banned":
        raise HTTPException(status_code=400, detail="该用户已被封禁，无法加入群聊")

    if target_user.id == user.id:
        raise HTTPException(status_code=400, detail="您已是该群成员，无需邀请自己")

    # 检查目标用户是否已在群内
    already_member = await get_group_member(db, room_id, target_user.id)
    if already_member:
        raise HTTPException(status_code=400, detail="该用户已是群成员")

    # 加入群聊（入群前的历史消息不计入未读）
    new_member = GroupMember(
        room_id=room_id,
        user_id=target_user.id,
        role="member",
        last_read_at=datetime.now(timezone.utc)
    )
    db.add(new_member)

    # 发送通知
    notif = Notification(
        receiver_id=target_user.id,
        sender_id=user.id,
        action_type="group_invite",
        target_id=room_id,
        brief=f"邀请你加入了群聊「{room.name}」"
    )
    db.add(notif)

    # 目标用户可能还留有 pending 申请，一并关闭，避免后续审批重复入群
    await db.execute(
        update(GroupJoinRequest)
        .where(
            GroupJoinRequest.room_id == room_id,
            GroupJoinRequest.user_id == target_user.id,
            GroupJoinRequest.status == "pending"
        )
        .values(status="approved", handled_by=user.id, updated_at=datetime.now(timezone.utc))
    )
    try:
        await db.commit()
    except IntegrityError:
        # 并发邀请撞 uq_group_room_user 唯一约束：对方已被其他人拉入
        await db.rollback()
        raise HTTPException(status_code=400, detail="该用户刚刚已加入群聊")

    # 实时通知被邀请人刷新群列表，并同步群内成员数变化
    await ws_manager.send_personal_message({
        "type": "group_joined",
        "room_id": str(room_id),
        "room_name": room.name
    }, str(target_user.id))
    await notify_group_members_changed(db, room_id, room.is_public, "member_invited")

    return {"message": f"已成功邀请 {target_user.username} 加入群聊"}

@router.post("/groups/{room_id}/kick")
async def kick_from_group(
    room_id: UUID,
    req: GroupKickRequest,
    request: Request,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """群主或管理员将成员移出群聊"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    # 操作人权限校验
    operator_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()
    is_super = user.role in ["super_admin", "admin"]
    if not is_super and (not operator_member or operator_member.role not in ["owner", "admin"]):
        raise HTTPException(status_code=403, detail="仅群主或群管理员有权移出成员")

    if req.user_id == user.id:
        raise HTTPException(status_code=400, detail="不能将自己踢出群聊，如需退出请使用退出功能")

    # 检查被踢成员
    target_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == req.user_id)
    )).scalar_one_or_none()
    if not target_member:
        raise HTTPException(status_code=404, detail="该用户不在群聊中")

    if target_member.role == "owner":
        raise HTTPException(status_code=403, detail="无法将群主移出群聊")

    if not is_super and operator_member and operator_member.role == "admin" and target_member.role == "admin":
        raise HTTPException(status_code=403, detail="群管理员不可相互移出")

    await db.delete(target_member)
    # 权限回收必须同时生效于实时通道：成员关系删除后仍留着的订阅会继续收到私密群消息
    ws_manager.unsubscribe(str(req.user_id), str(room_id))

    await log_group_penetration(
        db, room, user, "kick",
        target_user_id=req.user_id,
        detail=f"移除成员 {req.user_id}",
        client_ip=request.client.host if request.client else ""
    )

    # 通知被移出的成员（target_id 置空：对方已无权进入该群，避免无效跳转）
    db.add(Notification(
        receiver_id=req.user_id,
        sender_id=user.id,
        action_type="group_kicked",
        target_id=None,
        brief=f"您已被移出群聊「{room.name}」"
    ))
    await db.commit()

    # 被踢成员客户端立即移除该群，其他成员同步成员数
    await ws_manager.send_personal_message({
        "type": "group_kicked",
        "room_id": str(room_id),
        "room_name": room.name
    }, str(req.user_id))
    await notify_group_members_changed(db, room_id, room.is_public, "member_kicked")
    return {"message": "已成功移出该成员"}

@router.get("/groups/{room_id}/members", response_model=List[GroupMemberResponse])
async def list_group_members(
    room_id: UUID,
    request: Request,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """获取指定群聊的全体成员列表"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    # 私密群成员名单仅群内成员与站长可见，防止经接口枚举私密群成员
    if not room.is_public and not _is_site_admin(user):
        if not await get_group_member(db, room_id, user.id):
            raise HTTPException(status_code=403, detail="该群为私密好友群，仅群成员可查看成员名单")

    # 站长穿透查看他人私密群成员时留痕
    if await log_group_penetration(db, room, user, "list_members",
                                   client_ip=request.client.host if request.client else ""):
        await db.commit()

    # 查询成员列表并预加载用户资料
    q = (
        select(GroupMember)
        .options(selectinload(GroupMember.user))
        .where(GroupMember.room_id == room_id)
        .order_by(GroupMember.joined_at.asc())
    )
    res = await db.execute(q)
    members = list(res.scalars().all())

    # 历史群聊容错：若未记录群主，以创建者做只读兜底（不在 GET 中写库，避免并发唯一键冲突）
    if not any(m.role == "owner" for m in members) and room.creator_id:
        creator_u = (await db.execute(select(User).where(User.id == room.creator_id))).scalar_one_or_none()
        if creator_u:
            virtual_owner = GroupMember(
                room_id=room_id,
                user_id=room.creator_id,
                role="owner",
                joined_at=room.created_at
            )
            virtual_owner.user = creator_u
            members = [virtual_owner, *members]

    # 按 角色(群主>管理员>普通成员) 与 加入时间 排序
    role_priority = {"owner": 1, "admin": 2, "member": 3}
    members_sorted = sorted(
        members,
        key=lambda m: (role_priority.get(m.role, 4), m.joined_at or datetime.min.replace(tzinfo=timezone.utc))
    )

    return [
        GroupMemberResponse(
            user_id=m.user_id,
            username=m.user.username if m.user else "未知极客",
            nickname=m.user.nickname if m.user else None,
            avatar_url=m.user.avatar_url if m.user else "",
            level=m.user.level if m.user else 1,
            role=m.role,
            joined_at=m.joined_at,
            group_level=int(getattr(m, "group_level", 1) or 1),
            group_exp=int(getattr(m, "group_exp", 0) or 0),
            badge_id=getattr(m, "badge_id", None),
            badge_name=group_badge_of(m)[1],
            special_badge_name=getattr(m, "special_badge_name", None),
            badge_kind="special" if (getattr(m, "special_badge_name", None) or "").strip() else "level",
            muted_until=getattr(m, "muted_until", None),
        )
        for m in members_sorted if m.user
    ]

@router.post("/groups/{room_id}/members/{target_user_id}/role")
async def update_group_member_role(
    room_id: UUID,
    target_user_id: UUID,
    req: GroupMemberRoleRequest,
    request: Request,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """群主设为/撤销群管理员"""
    # 行锁：名额校验是「先 count 再改」，并发下会同时看到 count=4 一起放行 → 出现第 6 位管理员
    room = (await db.execute(
        select(GroupRoom).where(GroupRoom.id == room_id).with_for_update()
    )).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    # 检查操作者是否为群主或超级管理员
    is_super = user.role in ["super_admin", "admin"]
    operator_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()

    if not is_super and (not operator_member or operator_member.role != "owner"):
        raise HTTPException(status_code=403, detail="仅群主有权设置或撤销管理员")

    if target_user_id == user.id:
        raise HTTPException(status_code=400, detail="不能更改自己的群主身份")

    target_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == target_user_id)
    )).scalar_one_or_none()
    if not target_member:
        raise HTTPException(status_code=404, detail="目标用户不在该群聊中")

    if target_member.role == "owner":
        raise HTTPException(status_code=400, detail="不能更改群主的角色")

    # 管理员名额：1 位群主 + 最多 5 位管理员，所有入口（含站长操作）统一受限
    if req.role == "admin" and target_member.role != "admin":
        if await _count_group_admins(db, room_id) >= GROUP_ADMIN_LIMIT:
            raise HTTPException(
                status_code=400,
                detail=f"每个群最多 {GROUP_ADMIN_LIMIT} 位管理员（1 位群主 + {GROUP_ADMIN_LIMIT} 位管理员），请先撤销一位",
            )

    target_member.role = req.role
    await log_group_penetration(
        db, room, user, "role",
        target_user_id=target_user_id,
        detail=f"设置角色 {req.role}",
        client_ip=request.client.host if request.client else ""
    )
    await db.commit()

    # 群内所有客户端同步角色徽标与成员名单
    await notify_group_members_changed(db, room_id, room.is_public, "role_changed")
    role_name = "管理员" if req.role == "admin" else "普通成员"
    return {"message": f"已成功将该成员身份变更为「{role_name}」"}

@router.post("/groups/{room_id}/transfer-owner")
async def transfer_group_owner(
    room_id: UUID,
    req: GroupTransferOwnerRequest,
    request: Request,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """群主转让：把群主身份移交给指定成员，原群主自动降为管理员。

    群主长期不在线时，群会卡在"没人能管理"的状态，所以需要这条移交通道。
    原群主保留管理员权限，避免误操作后彻底失去管理能力。
    """
    # 与设置角色共用同一把群行锁，避免并发转让 + 并发设管理员把名额撑破
    room = (await db.execute(
        select(GroupRoom).where(GroupRoom.id == room_id).with_for_update()
    )).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    is_super = user.role in ["super_admin", "admin"]
    operator_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()

    if not is_super and (not operator_member or operator_member.role != "owner"):
        raise HTTPException(status_code=403, detail="仅群主可以转让群主身份")

    if req.target_user_id == user.id:
        raise HTTPException(status_code=400, detail="群主身份已经在您身上了")

    target_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == req.target_user_id)
    )).scalar_one_or_none()
    if not target_member:
        raise HTTPException(status_code=404, detail="目标用户不在该群聊中")

    target_user = (await db.execute(select(User).where(User.id == req.target_user_id))).scalar_one_or_none()
    if not target_user or target_user.status in ("banned", "deactivated", "deleting"):
        raise HTTPException(status_code=400, detail="该成员当前状态不支持接收群主")

    # 原群主降为管理员（不是普通成员），避免移交后立刻失去管理能力；
    # 但若管理员名额已满，只能降为普通成员，否则会出现第 6 位管理员
    demote_note = ""
    if operator_member:
        admin_count = await _count_group_admins(db, room_id)
        # 接收方本来是管理员时，会让出一个名额
        effective = admin_count - (1 if target_member.role == "admin" else 0)
        if effective >= GROUP_ADMIN_LIMIT:
            operator_member.role = "member"
            demote_note = "（管理员名额已满，您已降为普通成员）"
        else:
            operator_member.role = "admin"
    else:
        # 站长代操作：操作者通常不在群里，真正需要降级的是「原群主」。
        # 不处理会出现两个 owner，破坏 1 位群主的不变量。
        current_owner = (await db.execute(
            select(GroupMember).where(
                GroupMember.room_id == room_id,
                GroupMember.role == "owner",
                GroupMember.user_id != req.target_user_id,
            ).limit(1)
        )).scalar_one_or_none()
        if current_owner:
            admin_count = await _count_group_admins(db, room_id)
            effective = admin_count - (1 if target_member.role == "admin" else 0)
            current_owner.role = "member" if effective >= GROUP_ADMIN_LIMIT else "admin"
    target_member.role = "owner"
    room.creator_id = req.target_user_id
    room.updated_at = datetime.now(timezone.utc)

    await log_group_penetration(
        db, room, user, "transfer_owner",
        target_user_id=req.target_user_id,
        detail=f"群主转让给 {target_user.username}",
        client_ip=request.client.host if request.client else ""
    )
    await db.commit()

    # 广播成员变更，所有客户端刷新角色徽标
    await notify_group_members_changed(db, room_id, room.is_public, "owner_changed")
    return {
        "message": f"群主已转让{demote_note or '，您现在是群管理员'}",
        "new_owner_username": target_user.username,
        "new_owner_nickname": target_user.nickname,
    }


@router.post("/groups/{room_id}/leave")
async def leave_group_room(
    room_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """主动退出群聊"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    my_member = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
    )).scalar_one_or_none()
    if not my_member:
        raise HTTPException(status_code=400, detail="您不在该群聊中")

    # 如果是群主退出
    if my_member.role == "owner":
        other_m = (await db.execute(
            select(GroupMember)
            .where(GroupMember.room_id == room_id, GroupMember.user_id != user.id)
            .order_by(GroupMember.joined_at.asc())
            .limit(1)
        )).scalar_one_or_none()
        if other_m:
            other_m.role = "owner"
            room.creator_id = other_m.user_id
            # 通知新群主接任，避免群主交接静默发生
            db.add(Notification(
                receiver_id=other_m.user_id,
                sender_id=user.id,
                action_type="group_owner_transferred",
                target_id=room_id,
                brief=f"原群主已退出，您已成为群聊「{room.name}」的新群主"
            ))
        else:
            # 只有群主一人，直接解散群
            ws_manager.unsubscribe(str(user.id), str(room_id))
            await db.delete(room)
            await db.commit()
            return {"message": "群聊已自动解散"}

    is_public = room.is_public
    await db.delete(my_member)
    ws_manager.unsubscribe(str(user.id), str(room_id))
    await db.commit()

    # 实时同步：让该成员自己的其他终端移除该群，并刷新其余成员的成员数
    await ws_manager.send_personal_message({
        "type": "group_left",
        "room_id": str(room_id)
    }, str(user.id))
    await notify_group_members_changed(db, room_id, is_public, "member_left")
    return {"message": "已成功退出群聊"}

@router.delete("/groups/{room_id}")
async def delete_group_room(
    room_id: UUID,
    request: Request,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """解散群聊频道（群主或超级管理员专属）"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    is_super = _is_site_admin(user)
    is_owner = room.creator_id == user.id
    if not is_super and not is_owner:
        # 检查是否为群主角色
        my_m = await get_group_member(db, room_id, user.id)
        if not my_m or my_m.role != "owner":
            raise HTTPException(status_code=403, detail="仅群主或站长有权解散该群聊")

    is_public = room.is_public
    room_name = room.name
    # 站长越权解散他人私密群需留痕（日志与解散在同一事务内落库）
    await log_group_penetration(
        db, room, user, "dissolve",
        detail=f"解散群聊「{room_name}」",
        client_ip=request.client.host if request.client else ""
    )

    # 先取出成员列表：群删除后成员关系级联清空，届时无法再定位推送对象
    targets = set(await get_group_member_ids(db, room_id))
    if is_public:
        targets |= ws_manager.room_subscribers(str(room_id))

    await db.delete(room)
    await db.commit()

    # 群已解散：把该房间的所有内存订阅一并清空，避免残留订阅继续收到事件
    for uid in list(targets):
        ws_manager.unsubscribe(str(uid), str(room_id))

    # 实时通知相关在线用户：该群已解散，客户端需立即移除
    await ws_manager.send_to_users({
        "type": "group_closed",
        "room_id": str(room_id),
        "room_name": room_name
    }, targets)
    return {"message": "群聊已成功解散"}

@router.get("/groups/{room_id}/messages", response_model=List[GroupMessageResponse])
async def get_group_messages(
    room_id: UUID,
    request: Request,
    page: int = Query(1, ge=1),
    size: int = Query(50, ge=1, le=100),
    before_seq_id: Optional[int] = Query(None),
    response: Response = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """获取指定群聊频道的历史消息"""
    room_q = select(GroupRoom).where(GroupRoom.id == room_id)
    room = (await db.execute(room_q)).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")

    # 私密好友群：仅群成员、群主或全站管理员可拉取聊天记录
    if not room.is_public and user.role not in ["super_admin", "admin"]:
        is_member = await get_group_member(db, room_id, user.id)
        if not is_member and room.creator_id != user.id:
            raise HTTPException(status_code=403, detail="该群为私密好友群，仅群成员可查看历史记录")

    # 站长穿透读取他人私密群历史时留痕（仅站长的越权读取会产生记录）
    if await log_group_penetration(db, room, user, "read_messages",
                                   detail=f"page={page},size={size},cursor={before_seq_id}",
                                   client_ip=request.client.host if request.client else ""):
        await db.commit()

    offset = (page - 1) * size
    # 向上翻页优先用 seq_id 游标：爬楼期间 WS 不断追加新消息，offset 会整体前移
    # 造成重复或漏消息；游标锚定「已加载的最早一条」则完全不受新消息影响。
    use_cursor = before_seq_id is not None
    try:
        q = (
            select(GroupMessage)
            .options(selectinload(GroupMessage.sender))
            .where(GroupMessage.room_id == room_id)
        )
        if use_cursor:
            q = q.where(GroupMessage.seq_id < before_seq_id)
        else:
            q = q.offset(offset)
        q = q.order_by(GroupMessage.created_at.desc(), GroupMessage.seq_id.desc().nulls_last()).limit(size + 1)
        res = await db.execute(q)
        messages = list(res.scalars().all())
    except Exception as e:
        print(f"[Chat WARN] 群消息查询降级排序: {e}")
        q = (
            select(GroupMessage)
            .options(selectinload(GroupMessage.sender))
            .where(GroupMessage.room_id == room_id)
        )
        if use_cursor:
            q = q.where(GroupMessage.seq_id < before_seq_id)
        else:
            q = q.offset(offset)
        q = q.order_by(GroupMessage.created_at.desc()).limit(size + 1)
        res = await db.execute(q)
        messages = list(res.scalars().all())

    # 多取 1 条判断 has_more，避免总数恰好整除时多一次空请求
    has_more = len(messages) > size
    messages = messages[:size]
    next_cursor = min((m.seq_id for m in messages if m.seq_id is not None), default=None)
    if response is not None:
        response.headers["X-Has-More"] = "1" if has_more else "0"
        if next_cursor is not None:
            response.headers["X-Next-Cursor"] = str(next_cursor)

    messages.reverse()

    return [
        GroupMessageResponse(
            id=m.id,
            room_id=m.room_id,
            sender_id=m.sender_id,
            sender_username=m.sender.username if m.sender else "已注销用户",
            sender_nickname=m.sender.nickname if m.sender else None,
            sender_avatar=m.sender.avatar_url if m.sender else "",
            sender_level=m.sender.level if m.sender else 1,
            content="[该消息已撤回]" if getattr(m, 'is_recalled', False) else m.content,
            msg_type=getattr(m, 'msg_type', 'text') or 'text',
            seq_id=getattr(m, 'seq_id', None),
            is_recalled=getattr(m, 'is_recalled', False),
            is_mention_all=getattr(m, 'is_mention_all', False),
            created_at=m.created_at,
            reply_to_id=getattr(m, 'reply_to_id', None),
            reply_sender=getattr(m, 'reply_sender', '') or '',
            # 历史快照里可能存着图片存储路径：读取时统一再清洗一次，避免旧数据继续外泄
            reply_snippet=_reply_snippet_of(getattr(m, 'reply_snippet', '') or ''),
        ) for m in messages
    ]


@router.post("/groups/{room_id}/read")
async def mark_group_read(
    room_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """将指定群聊标记为已读（更新成员的最后阅读时间，用于未读数计算）"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")

    member = await get_group_member(db, room_id, user.id)
    if not member:
        # 非群成员没有未读概念，直接返回成功，避免前端轮询报错
        return {"success": True}

    member.last_read_at = datetime.now(timezone.utc)
    await db.commit()
    return {"success": True}

@router.post("/groups/{room_id}/messages/{msg_id}/recall")
async def recall_group_message(
    room_id: UUID,
    msg_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """限时撤回群聊消息 (发送者2分钟内可撤回，群主/管理员/超级管理员可随时撤回)"""
    room_q = select(GroupRoom).where(GroupRoom.id == room_id)
    room = (await db.execute(room_q)).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")

    msg_q = select(GroupMessage).where(GroupMessage.id == msg_id, GroupMessage.room_id == room_id)
    msg = (await db.execute(msg_q)).scalar_one_or_none()
    if not msg:
        raise HTTPException(status_code=404, detail="消息不存在")

    if msg.is_recalled:
        return {"message": "消息已撤回"}

    # 权限检查：群主/群管理员/站长可撤回任意群消息，普通成员只能撤回自己 2 分钟内的消息
    is_privileged = False
    if user.role in ["super_admin", "admin"]:
        is_privileged = True
    else:
        member = (await db.execute(
            select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == user.id)
        )).scalar_one_or_none()
        if member and member.role in ["owner", "admin"]:
            is_privileged = True

    if not is_privileged:
        if msg.sender_id != user.id:
            raise HTTPException(status_code=403, detail="无权撤回他人的群消息")
        now = datetime.now(timezone.utc)
        created_at = _as_utc(msg.created_at)
        if created_at and now - created_at > timedelta(minutes=2):
            raise HTTPException(status_code=400, detail="发送已超过 2 分钟，无法撤回")

    msg.is_recalled = True
    await db.commit()

    # 通过 WebSocket 广播群消息撤回
    await push_group_event(db, room_id, room.is_public, {
        "type": "group_recall",
        "room_id": str(room_id),
        "message_id": str(msg.id),
        "operator_id": str(user.id),
        "operator_name": user.nickname or user.username
    })

    return {"message": "群消息已成功撤回"}

async def notify_group_mentions(
    db: AsyncSession,
    room: "GroupRoom",
    sender: User,
    content: str,
    msg_id,
    sender_name: str,
):
    """群消息里被 @ 的成员单独推一帧。

    是否提示由前端决定（群/私聊的免打扰设置存在本地，服务端不知道），
    这里只负责把「谁被 @ 了、在哪条消息」送达到在线终端。
    """
    text = content or ""
    if "@" not in text:
        return
    rows = (await db.execute(
        select(GroupMember.user_id, User.username, User.nickname)
        .join(User, User.id == GroupMember.user_id)
        .where(GroupMember.room_id == room.id)
    )).all()
    if not rows:
        return

    lower = text.lower()
    targets = set()
    for uid, username, nickname in rows:
        if str(uid) == str(sender.id):
            continue
        names = [n for n in (username, nickname) if n]
        if any(f"@{str(n).lower()}" in lower for n in names):
            targets.add(str(uid))

    for uid in targets:
        try:
            await ws_manager.send_personal_message(
                {
                    "type": "mention",
                    "room_id": str(room.id),
                    "room_name": room.name,
                    "msg_id": str(msg_id),
                    "sender": sender_name,
                    "snippet": text[:60],
                },
                uid,
            )
        except Exception:
            pass


@router.post("/groups/{room_id}/messages", response_model=GroupMessageResponse)
async def send_group_message_http(
    room_id: UUID,
    req: MessageCreateRequest,
    request: Request,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db)
):
    """向群聊频道发送消息 (HTTP 接口)"""
    room_q = select(GroupRoom).where(GroupRoom.id == room_id)
    room = (await db.execute(room_q)).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")

    # 校验发言者是否已在群内 (群主/创建者具有天然发言权)
    member = await get_group_member(db, room_id, user.id)
    if not member and room.creator_id != user.id and not _is_site_admin(user):
        if room.is_public:
            raise HTTPException(status_code=403, detail="您尚未加入该公开频道，请先点击加入后再发言")
        else:
            raise HTTPException(status_code=403, detail="您不是该私密好友群成员，无权发送消息")

    # 禁言校验（与 WS 通道共用）
    mute_err = await _check_group_mute(room, user, member)
    if mute_err:
        raise HTTPException(status_code=403, detail=mute_err)

    # 站长以非成员身份向私密群发言需留痕
    if not member and room.creator_id != user.id:
        await log_group_penetration(
            db, room, user, "send_message",
            detail="以站长身份在非成员私密群发言",
            client_ip=request.client.host if request.client else ""
        )

    content = req.content.strip()
    if not content:
        raise HTTPException(status_code=400, detail="消息内容不能为空")
    if len(content) > MAX_MESSAGE_LEN:
        raise HTTPException(status_code=400, detail=f"单条消息不能超过 {MAX_MESSAGE_LEN} 个字符")

    is_blocked, _, clean_content = sensitive_filter.scan(content)
    if is_blocked:
        raise HTTPException(status_code=400, detail="消息包含违规敏感词汇，发送被拦截")

    # 表情包刷屏保护：每分钟最多 5 个
    if is_sticker_content(clean_content) and not await _sticker_rate_ok(user.id):
        raise HTTPException(status_code=429, detail="表情发得太快啦，稍等几秒再发")

    if is_image_message(clean_content) and not is_sticker_content(clean_content):
        from app.services.image_quota import ImageQuotaExceeded, consume_image_quota

        try:
            await consume_image_quota(user, "group")
        except ImageQuotaExceeded as exc:
            raise HTTPException(status_code=429, detail=str(exc))

    # @全体成员：权限 + 当日额度（群主/管理员每天 10 次）
    is_mention_all, mention_code, mention_error = await _check_mention_all(
        db, room, user, member, clean_content
    )
    if mention_error:
        raise HTTPException(status_code=mention_code or 400, detail=mention_error)

    reply_to_id, reply_sender, reply_snippet = await _group_reply_snapshot(
        db, room_id, getattr(req, "reply_to_message_id", None)
    )

    new_msg = GroupMessage(
        id=uuid.uuid4(),
        room_id=room_id,
        sender_id=user.id,
        content=clean_content,
        msg_type="text",
        seq_id=await next_seq_id(),
        is_recalled=False,
        is_mention_all=is_mention_all,
        created_at=datetime.now(timezone.utc),
        reply_to_id=reply_to_id,
        reply_sender=reply_sender,
        reply_snippet=reply_snippet,
    )
    checkin_type, _, checkin_n = await maybe_group_checkin(db, room, member, clean_content)
    new_msg.msg_type = checkin_type
    db.add(new_msg)
    if member:
        # 自己发言等同于已读该群，避免自己触发自己的未读红点
        member.last_read_at = datetime.now(timezone.utc)
    await award_group_exp(db, member, clean_content)
    await db.commit()
    await db.refresh(new_msg)

    group_payload = {
        "type": "group_msg",
        "room_id": str(room_id),
        "msg_id": str(new_msg.id),
        "sender_id": str(user.id),
        "sender_username": user.username,
        "sender_nickname": user.nickname,
        "sender_avatar": user.avatar_url or "",
        "sender_level": user.level,
        "content": clean_content,
        "msg_type": new_msg.msg_type,
        "seq_id": new_msg.seq_id,
        "is_recalled": False,
        "is_mention_all": is_mention_all,
        "client_msg_id": getattr(req, "client_msg_id", None) or None,
        "reply_to_id": str(reply_to_id) if reply_to_id else None,
        "reply_sender": reply_sender,
        "reply_snippet": reply_snippet,
        "created_at": format_utc_iso(new_msg.created_at)
    }

    # 公共频道全网广播；私密好友群仅定向推送给群内成员与订阅者，确保发送方必能收到
    await push_group_event(db, room_id, room.is_public, group_payload, extra_target_id=str(user.id))

    # @全体成员 → 全员提醒；普通 @ → 只提醒被点名的人
    if is_mention_all:
        await notify_group_mention_all(db, room, user, new_msg.id, user.nickname or user.username)
    else:
        await notify_group_mentions(
            db, room, user, clean_content, new_msg.id, user.nickname or user.username
        )

    return GroupMessageResponse(
        id=new_msg.id,
        room_id=new_msg.room_id,
        sender_id=user.id,
        sender_username=user.username,
        sender_nickname=user.nickname,
        sender_avatar=user.avatar_url or "",
        sender_level=user.level,
        content=new_msg.content,
        msg_type=new_msg.msg_type,
        seq_id=new_msg.seq_id,
        is_recalled=new_msg.is_recalled,
        is_mention_all=new_msg.is_mention_all,
        created_at=new_msg.created_at,
        reply_to_id=new_msg.reply_to_id,
        reply_sender=new_msg.reply_sender,
        reply_snippet=new_msg.reply_snippet,
    )

# ==================== 群公告历史（置顶 / 已读确认 / 历史留存） ====================

def _announcement_readable(room: GroupRoom, user: User, member: Optional[GroupMember]) -> bool:
    """公开群允许非成员查看公告；私密群仅成员/群主/站内管理员可见。"""
    if _is_site_admin(user) or room.creator_id == user.id or member:
        return True
    return bool(room.is_public)


def _announcement_out(ann: GroupAnnouncement, author: Optional[User], read_at=None) -> GroupAnnouncementResponse:
    return GroupAnnouncementResponse(
        id=ann.id,
        room_id=ann.room_id,
        content=ann.content,
        created_by=ann.created_by,
        created_by_username=(author.username if author else None),
        created_by_nickname=(author.nickname if author else None),
        created_at=ann.created_at,
        is_read=read_at is not None,
        read_at=read_at,
    )


@router.get("/groups/{room_id}/announcements", response_model=List[GroupAnnouncementResponse])
async def list_group_announcements(
    room_id: UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """群公告历史：按时间倒序返回，附带当前用户的已读状态。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在或已解散")
    member = await get_group_member(db, room_id, user.id)
    if not _announcement_readable(room, user, member):
        raise HTTPException(status_code=403, detail="您不是该群成员，无法查看群公告")

    rows = (await db.execute(
        select(GroupAnnouncement, User)
        .outerjoin(User, User.id == GroupAnnouncement.created_by)
        .where(GroupAnnouncement.room_id == room_id, GroupAnnouncement.is_deleted.is_(False))
        .order_by(GroupAnnouncement.created_at.desc())
    )).all()

    read_map: dict = {}
    if rows:
        read_rows = (await db.execute(
            select(GroupAnnouncementRead.announcement_id, GroupAnnouncementRead.read_at).where(
                GroupAnnouncementRead.user_id == user.id,
                GroupAnnouncementRead.announcement_id.in_([a.id for a, _ in rows]),
            )
        )).all()
        read_map = {aid: rat for aid, rat in read_rows}

    return [_announcement_out(ann, author, read_map.get(ann.id)) for ann, author in rows]


@router.post("/groups/{room_id}/announcements", response_model=GroupAnnouncementResponse)
async def create_group_announcement(
    room_id: UUID,
    req: GroupAnnouncementCreateRequest,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db),
):
    """发布群公告：仅群主 / 群管理员 / 站内管理员。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在或已解散")
    member = await get_group_member(db, room_id, user.id)
    if not _is_group_manager(room, user, member):
        raise HTTPException(status_code=403, detail="只有群主或管理员可以发布群公告")

    content = (req.content or "").strip()
    if not content:
        raise HTTPException(status_code=400, detail="公告内容不能为空")

    now = datetime.now(timezone.utc)
    ann = GroupAnnouncement(
        id=uuid.uuid4(),
        room_id=room_id,
        content=content,
        created_by=user.id,
        created_at=now,
    )
    db.add(ann)
    # 先落公告行，再写已读记录：两者没有 ORM 关系，flush 顺序不保证会撞外键
    await db.flush()
    # 发布者本人自动视为已读，避免刚发完就被自己的公告顶在聊天窗顶部
    db.add(GroupAnnouncementRead(
        id=uuid.uuid4(), announcement_id=ann.id, room_id=room_id, user_id=user.id, read_at=now
    ))
    # 旧字段继续同步，保证未升级的客户端/群资料卡片仍能展示最新公告
    room.announcement = content
    room.announcement_updated_at = now
    await db.commit()
    await db.refresh(ann)

    await push_group_event(db, room_id, room.is_public, {
        "type": "group_announcement",
        "room_id": str(room_id),
        "announcement_id": str(ann.id),
        "content": content,
        "sender_id": str(user.id),
        "sender": user.nickname or user.username,
        "created_at": format_utc_iso(now),
    })

    return _announcement_out(ann, user, now)


@router.delete("/groups/{room_id}/announcements/{announcement_id}")
async def delete_group_announcement(
    room_id: UUID,
    announcement_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db),
):
    """删除历史公告（软删除）：仅群主 / 群管理员 / 站内管理员。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在或已解散")
    member = await get_group_member(db, room_id, user.id)
    if not _is_group_manager(room, user, member):
        raise HTTPException(status_code=403, detail="只有群主或管理员可以删除群公告")

    ann = (await db.execute(
        select(GroupAnnouncement).where(
            GroupAnnouncement.id == announcement_id,
            GroupAnnouncement.room_id == room_id,
            GroupAnnouncement.is_deleted.is_(False),
        )
    )).scalar_one_or_none()
    if not ann:
        raise HTTPException(status_code=404, detail="公告不存在或已被删除")

    now = datetime.now(timezone.utc)
    ann.is_deleted = True
    ann.deleted_at = now
    ann.deleted_by = user.id

    # 删的是最新一条时，把群资料里的旧字段回退到剩下的最新公告，避免展示已删除内容
    latest = (await db.execute(
        select(GroupAnnouncement)
        .where(
            GroupAnnouncement.room_id == room_id,
            GroupAnnouncement.is_deleted.is_(False),
            GroupAnnouncement.id != announcement_id,
        )
        .order_by(GroupAnnouncement.created_at.desc())
        .limit(1)
    )).scalar_one_or_none()
    room.announcement = (latest.content if latest else "")
    room.announcement_updated_at = now
    await db.commit()

    await push_group_event(db, room_id, room.is_public, {
        "type": "group_announcement_deleted",
        "room_id": str(room_id),
        "announcement_id": str(announcement_id),
    })
    return {"ok": True}


@router.post("/groups/{room_id}/announcements/{announcement_id}/read")
async def read_group_announcement(
    room_id: UUID,
    announcement_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db),
):
    """确认已读：写入已读记录后，该公告不再在聊天窗顶部置顶。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在或已解散")
    member = await get_group_member(db, room_id, user.id)
    if not _announcement_readable(room, user, member):
        raise HTTPException(status_code=403, detail="您不是该群成员，无法确认群公告")

    ann = (await db.execute(
        select(GroupAnnouncement).where(
            GroupAnnouncement.id == announcement_id,
            GroupAnnouncement.room_id == room_id,
            GroupAnnouncement.is_deleted.is_(False),
        )
    )).scalar_one_or_none()
    if not ann:
        raise HTTPException(status_code=404, detail="公告不存在或已被删除")

    now = datetime.now(timezone.utc)
    existing = (await db.execute(
        select(GroupAnnouncementRead).where(
            GroupAnnouncementRead.announcement_id == announcement_id,
            GroupAnnouncementRead.user_id == user.id,
        )
    )).scalar_one_or_none()
    if existing:
        read_at = existing.read_at
    else:
        db.add(GroupAnnouncementRead(
            id=uuid.uuid4(), announcement_id=announcement_id, room_id=room_id, user_id=user.id, read_at=now
        ))
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
        read_at = now
    return {"ok": True, "read_at": format_utc_iso(read_at)}


@router.get("/groups/{room_id}/mention-all-quota", response_model=MentionAllQuotaResponse)
async def get_mention_all_quota(
    room_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db),
):
    """查询当前用户今日剩余 @全体成员 次数（群主/管理员每天 10 次）。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在或已解散")
    member = await get_group_member(db, room_id, user.id)
    used = await _mention_all_used_today(db, user.id)
    remaining = max(0, MENTION_ALL_DAILY_LIMIT - used)
    return MentionAllQuotaResponse(
        limit=MENTION_ALL_DAILY_LIMIT,
        used=used,
        remaining=remaining,
        can_use=_is_group_manager(room, user, member) and remaining > 0,
    )

# ==================== WebSocket 长连接通讯网关 ====================
async def _ws_send(websocket: WebSocket, payload: dict):
    """安全发送：连接已断开时静默忽略，避免二次抛错影响其他逻辑"""
    try:
        await websocket.send_text(json.dumps(payload, ensure_ascii=False))
    except Exception:
        pass


async def _ws_error(websocket: WebSocket, detail: str, client_msg_id: Optional[str] = None):
    payload = {"type": "error", "detail": detail}
    if client_msg_id:
        payload["client_msg_id"] = client_msg_id
    await _ws_send(websocket, payload)


async def _handle_private_message(websocket: WebSocket, user_uuid: UUID, data: dict):
    """私聊消息：必须严格校验发送者属于该会话，杜绝越权向他人会话注入消息"""
    conv_id_str = data.get("conversation_id")
    content = str(data.get("content") or "").strip()
    client_msg_id = data.get("client_msg_id")

    if not conv_id_str or not content:
        await _ws_error(websocket, "消息内容不能为空", client_msg_id)
        return
    if len(content) > MAX_MESSAGE_LEN:
        await _ws_error(websocket, f"单条消息不能超过 {MAX_MESSAGE_LEN} 个字符", client_msg_id)
        return

    try:
        conv_id = UUID(str(conv_id_str))
    except (ValueError, TypeError):
        await _ws_error(websocket, "会话 ID 不合法", client_msg_id)
        return

    is_blocked, _, clean_content = sensitive_filter.scan(content)
    if is_blocked:
        await _ws_error(websocket, "消息包含违规敏感词汇，发送被拦截", client_msg_id)
        return

    async with AsyncSessionLocal() as db:
        # 0. 发送者状态实时校验：连接建立后被封禁/注销的用户不得继续发送私信
        sender = (await db.execute(select(User).where(User.id == user_uuid))).scalar_one_or_none()
        if not sender or sender.status in ("banned", "deactivated", "deleting"):
            await _ws_error(websocket, "账号已被封禁，无法发送消息", client_msg_id)
            try:
                await websocket.close(code=4003)
            except Exception:
                pass
            return

        conv = (await db.execute(select(Conversation).where(Conversation.id == conv_id))).scalar_one_or_none()
        if not conv:
            await _ws_error(websocket, "会话不存在", client_msg_id)
            return

        # 关键权限校验：只允许会话双方发言
        if conv.user_small_id != user_uuid and conv.user_big_id != user_uuid:
            await _ws_error(websocket, "无权在该会话发送消息", client_msg_id)
            return

        receiver_uuid = conv.user_big_id if conv.user_small_id == user_uuid else conv.user_small_id
        receiver_id = str(receiver_uuid)

        # 1. 黑名单双向拦截
        block_hit = (await db.execute(
            select(UserBlock).where(
                or_(
                    and_(UserBlock.blocker_id == user_uuid, UserBlock.blocked_id == receiver_uuid),
                    and_(UserBlock.blocker_id == receiver_uuid, UserBlock.blocked_id == user_uuid)
                )
            )
        )).scalar_one_or_none()
        if block_hit:
            await _ws_error(websocket, "消息发送被拦截：由于黑名单或隐私保护设置，对方拒收您的私信", client_msg_id)
            return

        # 2. 接收方免打扰模式
        rec_user = (await db.execute(select(User).where(User.id == receiver_uuid))).scalar_one_or_none()
        if rec_user and rec_user.dnd_enabled:
            f_check = (await db.execute(
                select(Follow).where(Follow.follower_id == receiver_uuid, Follow.following_id == user_uuid)
            )).scalar_one_or_none()
            if not f_check:
                await _ws_error(websocket, "对方已开启免打扰模式，仅接收其已关注极客的私信", client_msg_id)
                return

        # 表情包刷屏保护：每分钟最多 5 个
        if is_sticker_content(clean_content) and not await _sticker_rate_ok(sender.id):
            await _ws_error(websocket, "表情发得太快啦，稍等几秒再发", data.get("client_msg_id"))
            return

        if is_image_message(clean_content) and not is_sticker_content(clean_content):
            from app.services.image_quota import ImageQuotaExceeded, consume_image_quota

            try:
                await consume_image_quota(await db.get(User, user_uuid) or user_uuid, "direct")
            except ImageQuotaExceeded as exc:
                await _ws_error(websocket, str(exc), client_msg_id)
                return

        reply_to_id, reply_sender, reply_snippet = await _direct_reply_snapshot(
            db, conv_id, data.get("reply_to_message_id")
        )

        new_msg = Message(
            conversation_id=conv_id,
            sender_id=user_uuid,
            content=clean_content,
            seq_id=await next_seq_id(),
            reply_to_id=reply_to_id,
            reply_sender=reply_sender,
            reply_snippet=reply_snippet,
        )
        db.add(new_msg)
        conv.last_message_preview = _preview_text(clean_content)
        conv.last_message_at = datetime.now(timezone.utc)
        # 新消息到达后恢复双方视图
        conv.user_small_deleted_at = None
        conv.user_big_deleted_at = None

        # 写入系统消息通知中心
        db.add(Notification(
            receiver_id=receiver_uuid,
            sender_id=user_uuid,
            action_type="chat_message",
            target_id=conv_id,
            brief=f"发来私信: {clean_content[:50]}"
        ))
        await db.commit()
        await db.refresh(new_msg)

        # 发送方 ACK (前端据此把乐观消息替换为真实消息)
        await _ws_send(websocket, {
            "type": "ack",
            "client_msg_id": client_msg_id,
            "seq_id": new_msg.seq_id,
            "msg_id": str(new_msg.id),
            "created_at": format_utc_iso(new_msg.created_at)
        })

        # 接收方推送
        await ws_manager.send_personal_message({
            "type": "new_msg",
            "conversation_id": str(conv_id),
            "msg_id": str(new_msg.id),
            "sender_id": str(user_uuid),
            "content": clean_content,
            "seq_id": new_msg.seq_id,
            "reply_to_id": str(reply_to_id) if reply_to_id else None,
            "reply_sender": reply_sender,
            "reply_snippet": reply_snippet,
            "created_at": format_utc_iso(new_msg.created_at)
        }, receiver_id)


async def _handle_group_message(websocket: WebSocket, user_uuid: UUID, data: dict):
    """群聊消息：校验成员身份、长度与房间有效性后落库并广播"""
    room_id_str = data.get("room_id")
    content = str(data.get("content") or "").strip()
    if not room_id_str or not content:
        return
    if len(content) > MAX_MESSAGE_LEN:
        await _ws_error(websocket, f"单条消息不能超过 {MAX_MESSAGE_LEN} 个字符")
        return

    try:
        room_id = UUID(str(room_id_str))
    except (ValueError, TypeError):
        await _ws_error(websocket, "群聊 ID 不合法")
        return

    is_blocked, _, clean_content = sensitive_filter.scan(content)
    if is_blocked:
        await _ws_error(websocket, "消息包含违规敏感词汇，发送被拦截")
        return

    async with AsyncSessionLocal() as db:
        room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
        if not room:
            await _ws_error(websocket, "群聊频道不存在或已解散")
            return

        sender = (await db.execute(select(User).where(User.id == user_uuid))).scalar_one_or_none()
        if not sender:
            await _ws_error(websocket, "账号状态异常，请重新登录")
            return
        if sender.status == "banned":
            await _ws_error(websocket, "该账号已被封禁")
            return

        member = await get_group_member(db, room_id, sender.id)
        if not member and room.creator_id != sender.id and not _is_site_admin(sender):
            err_msg = "您尚未加入该公开频道，请先加入后再发言" if room.is_public else "您不是该私密好友群成员，无法发言"
            await _ws_error(websocket, err_msg)
            return

        if not member and room.creator_id != sender.id:
            # 站长以非成员身份在私密群发言，同样留痕
            await log_group_penetration(db, room, sender, "send_message", detail="WS 通道站长非成员发言")

        # 禁言校验（与 HTTP 通道共用）
        mute_err = await _check_group_mute(room, sender, member)
        if mute_err:
            await _ws_error(websocket, mute_err, data.get("client_msg_id"))
            return

        # 表情包刷屏保护：每分钟最多 5 个
        if is_sticker_content(clean_content) and not await _sticker_rate_ok(user_uuid):
            await _ws_error(websocket, "表情发得太快啦，稍等几秒再发", data.get("client_msg_id"))
            return

        if is_image_message(clean_content) and not is_sticker_content(clean_content):
            from app.services.image_quota import ImageQuotaExceeded, consume_image_quota

            try:
                await consume_image_quota(sender, "group")
            except ImageQuotaExceeded as exc:
                await _ws_error(websocket, str(exc), data.get("client_msg_id"))
                return

        # @全体成员：权限 + 当日额度（群主/管理员每天 10 次）
        is_mention_all, _mention_code, mention_error = await _check_mention_all(
            db, room, sender, member, clean_content
        )
        if mention_error:
            await _ws_error(websocket, mention_error, data.get("client_msg_id"))
            return

        reply_to_id, reply_sender, reply_snippet = await _group_reply_snapshot(
            db, room_id, data.get("reply_to_message_id")
        )

        new_msg = GroupMessage(
            id=uuid.uuid4(),
            room_id=room_id,
            sender_id=sender.id,
            content=clean_content,
            msg_type="text",
            seq_id=await next_seq_id(),
            is_recalled=False,
            is_mention_all=is_mention_all,
            created_at=datetime.now(timezone.utc),
            reply_to_id=reply_to_id,
            reply_sender=reply_sender,
            reply_snippet=reply_snippet,
        )
        checkin_type, _, checkin_n = await maybe_group_checkin(db, room, member, clean_content)
        new_msg.msg_type = checkin_type
        db.add(new_msg)
        if member:
            member.last_read_at = datetime.now(timezone.utc)
        await award_group_exp(db, member, clean_content)
        await db.commit()
        await db.refresh(new_msg)

        payload = {
            "type": "group_msg",
            "room_id": str(room_id),
            "msg_id": str(new_msg.id),
            "sender_id": str(sender.id),
            "sender_username": sender.username,
            "sender_nickname": sender.nickname,
            "sender_avatar": sender.avatar_url or "",
            "sender_level": sender.level,
            "content": clean_content,
            "msg_type": new_msg.msg_type,
            "seq_id": new_msg.seq_id,
            "is_recalled": False,
            "is_mention_all": is_mention_all,
            "client_msg_id": data.get("client_msg_id"),
            "reply_to_id": str(reply_to_id) if reply_to_id else None,
            "reply_sender": reply_sender,
            "reply_snippet": reply_snippet,
            "created_at": format_utc_iso(new_msg.created_at)
        }
        await push_group_event(db, room_id, room.is_public, payload, extra_target_id=str(sender.id))

        # WS 通道与 HTTP 路径保持一致：@全体成员 → 全员提醒；普通 @ → 只提醒被点名的人
        if is_mention_all:
            await notify_group_mention_all(db, room, sender, new_msg.id, sender.nickname or sender.username)
        else:
            await notify_group_mentions(
                db, room, sender, clean_content, new_msg.id, sender.nickname or sender.username
            )


async def _handle_typing(user_uuid: UUID, data: dict):
    """输入中状态：只有会话双方之间才会被转发，避免向任意用户推送骚扰帧"""
    conv_id_str = data.get("conversation_id")
    if not conv_id_str:
        return
    try:
        conv_id = UUID(str(conv_id_str))
    except (ValueError, TypeError):
        return

    async with AsyncSessionLocal() as db:
        conv = (await db.execute(select(Conversation).where(Conversation.id == conv_id))).scalar_one_or_none()
        if not conv or (conv.user_small_id != user_uuid and conv.user_big_id != user_uuid):
            return
        receiver_uuid = conv.user_big_id if conv.user_small_id == user_uuid else conv.user_small_id

    await ws_manager.broadcast_typing(str(user_uuid), str(receiver_uuid), str(conv_id))


async def _subscribe_room(user_uuid: UUID, room_id_str: Optional[str]) -> Optional[str]:
    """订阅房间推送。返回房间 ID 表示成功；私密群非成员直接拒绝订阅。"""
    if not room_id_str:
        return None
    try:
        room_id = UUID(str(room_id_str))
    except (ValueError, TypeError):
        return None

    async with AsyncSessionLocal() as db:
        room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
        if not room:
            return None
        if not room.is_public and not await get_group_member(db, room_id, user_uuid):
            return None

    # 一个连接同时只订阅一个房间，切换群聊时自动退订旧房间
    ws_manager.unsubscribe_all(str(user_uuid), except_room_id=str(room_id))
    ws_manager.subscribe(str(user_uuid), str(room_id))
    return str(room_id)


@router.websocket("/ws")
async def chat_websocket_endpoint(websocket: WebSocket):
    """
    鉴权令牌仅通过 Sec-WebSocket-Protocol 子协议传递：["access_token", "<JWT>"]。

    令牌绝不能出现在 URL query 中——nginx access_log 的 $request 会完整记录 query，
    WebSocket 握手同样会被记录，等于把长期有效的访问令牌写进日志与浏览器历史。
    """
    subprotocol = None
    token = None
    protocols = [
        p.strip()
        for p in (websocket.headers.get("sec-websocket-protocol") or "").split(",")
        if p.strip()
    ]
    if "access_token" in protocols:
        subprotocol = "access_token"
        idx = protocols.index("access_token")
        if idx + 1 < len(protocols):
            token = protocols[idx + 1]

    if not token:
        await websocket.close(code=4001)
        return

    payload = decode_token(token)
    if not payload or payload.get("type") != "access":
        await websocket.close(code=4001)
        return

    user_id = payload.get("sub")
    try:
        user_uuid = UUID(str(user_id))
    except (ValueError, TypeError):
        await websocket.close(code=4001)
        return

    # 建连前校验账号仍然有效：封禁/注销后旧 token 不应继续维持长连接
    async with AsyncSessionLocal() as db:
        current_user = (await db.execute(select(User).where(User.id == user_uuid))).scalar_one_or_none()
    if not current_user or current_user.status in ("banned", "deactivated", "deleting"):
        await websocket.close(code=4003)
        return

    # 强制改密期间与 HTTP 侧保持一致：只放行改密/登出/刷新，不允许建立长连接
    if getattr(current_user, "must_change_password", False):
        await websocket.close(code=4004)
        return

    # 令牌版本校验：与 deps.get_current_user_optional 保持一致，
    # 改密 / 封禁会自增 token_version，此处使旧令牌无法建立长连接
    token_ver = int(payload.get("ver", 0) or 0)
    if token_ver != int(getattr(current_user, "token_version", 0) or 0):
        await websocket.close(code=4001)
        return

    await ws_manager.connect(str(user_uuid), websocket, subprotocol=subprotocol)

    try:
        while True:
            data_text = await websocket.receive_text()

            # 报文解析失败只记录日志并跳过，不向对端发错误帧、更不断开连接
            try:
                data = json.loads(data_text)
            except (ValueError, TypeError):
                print(f"[WS WARN] 非法报文已忽略 user={user_uuid}")
                continue
            if not isinstance(data, dict):
                continue

            try:
                msg_type = data.get("type")

                # 1. 心跳探活
                if msg_type == "ping":
                    await _ws_send(websocket, {"type": "pong"})

                # 2. 输入中 (Typing...) 广播
                elif msg_type == "typing":
                    await _handle_typing(user_uuid, data)

                # 3. 房间订阅（公开频道非成员也可以实时接收）
                elif msg_type == "join_group":
                    joined = await _subscribe_room(user_uuid, data.get("room_id"))
                    await _ws_send(websocket, {"type": "joined_group", "room_id": joined})

                # 4. 退出房间订阅
                elif msg_type == "leave_group":
                    ws_manager.unsubscribe(str(user_uuid), str(data.get("room_id") or ""))

                # 4. 发送群聊消息
                elif msg_type == "group_msg":
                    # HTTP 群聊用 get_current_active_user（拒绝见习期），WS 必须一致，
                    # 否则见习生可以绕过 HTTP 限制直接通过长连接发言
                    if getattr(current_user, "status", "") == "probation":
                        await _ws_send(websocket, {"type": "error", "detail": "见习期内暂未开放群聊发言权限"})
                        continue
                    await _handle_group_message(websocket, user_uuid, data)

                # 5. 发送私聊消息
                elif msg_type == "msg":
                    await _handle_private_message(websocket, user_uuid, data)

            except WebSocketDisconnect:
                raise
            except Exception as exc:
                # 单帧解析/落库异常绝不能中断整条长连接，否则一次畸形报文就会导致掉线
                print(f"[WS WARN] 帧处理失败 user={user_uuid}: {exc}")
                await _ws_error(websocket, "消息处理失败，请稍后重试")

    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        ws_manager.disconnect(str(user_uuid), websocket)


# ==================== 消息搜索 / @我 ====================

def _escape_like(value: str) -> str:
    """转义 LIKE 通配符，避免用户输入 % 或 _ 时匹配过多。"""
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


@router.get("/groups/{room_id}/messages/search")
async def search_group_messages(
    room_id: UUID,
    q: str = Query(..., min_length=1, max_length=50),
    limit: int = Query(30, ge=1, le=100),
    request: Request = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """群内消息关键词搜索（仅当前可见消息，按时间倒序）。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊不存在")

    # 与 read_messages 保持完全一致的权限口径：
    # 私密群只有成员/群主/全站超级管理员与管理员可读，版主（moderator）不再放行。
    if not room.is_public and user.role not in ["super_admin", "admin"]:
        is_member = await get_group_member(db, room_id, user.id)
        if not is_member and room.creator_id != user.id:
            raise HTTPException(status_code=403, detail="该群为私密好友群，仅群成员可搜索历史记录")

    # 站长穿透搜索他人私密群时留痕（与读历史、发言路径一致）
    if await log_group_penetration(db, room, user, "search_messages",
                                   detail=f"q={q.strip()[:40]},limit={limit}",
                                   client_ip=request.client.host if request and request.client else ""):
        await db.commit()

    pattern = f"%{_escape_like(q.strip())}%"
    rows = (await db.execute(
        select(GroupMessage, User)
        .join(User, User.id == GroupMessage.sender_id)
        .where(
            GroupMessage.room_id == room_id,
            GroupMessage.is_recalled.is_(False),
            GroupMessage.content.ilike(pattern),
        )
        .order_by(GroupMessage.seq_id.desc())
        .limit(limit)
    )).all()

    return [
        {
            "id": str(m.id),
            "content": m.content,
            "msg_type": m.msg_type,
            "seq_id": m.seq_id,
            "created_at": format_utc_iso(m.created_at),
            "sender_username": u.username if u else "",
            "sender_nickname": (u.nickname if u else "") or "",
            "sender_avatar": (u.avatar_url if u else "") or "",
            "room_id": str(room_id),
            "room_name": room.name,
        }
        for m, u in rows
    ]


@router.get("/conversations/{conv_id}/messages/search")
async def search_private_messages(
    conv_id: UUID,
    q: str = Query(..., min_length=1, max_length=50),
    limit: int = Query(30, ge=1, le=100),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """私聊消息关键词搜索。"""
    conv = (await db.execute(select(Conversation).where(Conversation.id == conv_id))).scalar_one_or_none()
    if not conv or (conv.user_small_id != user.id and conv.user_big_id != user.id):
        raise HTTPException(status_code=403, detail="无权查看该会话")

    peer_id = conv.user_big_id if conv.user_small_id == user.id else conv.user_small_id
    peer = (await db.execute(select(User).where(User.id == peer_id))).scalar_one_or_none()

    pattern = f"%{_escape_like(q.strip())}%"
    rows = (await db.execute(
        select(Message)
        .where(
            Message.conversation_id == conv_id,
            Message.is_recalled.is_(False),
            Message.content.ilike(pattern),
        )
        .order_by(Message.seq_id.desc())
        .limit(limit)
    )).scalars().all()

    return [
        {
            "id": str(m.id),
            "content": m.content,
            "seq_id": m.seq_id,
            "created_at": format_utc_iso(m.created_at),
            "sender_username": peer.username if (peer and m.sender_id == peer_id) else user.username,
            "sender_nickname": (
                (peer.nickname if peer else "") or ""
                if m.sender_id == peer_id
                else (user.nickname or user.username)
            ),
            "conversation_id": str(conv_id),
            "conversation_name": (peer.nickname or peer.username) if peer else "",
        }
        for m in rows
    ]


@router.get("/mentions")
async def my_group_mentions(
    limit: int = Query(30, ge=1, le=100),
    days: int = Query(30, ge=1, le=180),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """「@我」的消息列表：扫描我所在群里最近提及我的消息。

    群聊的 @ 不进入通知表（通知表是评论/点赞那套），
    因此这里直接按「群成员 + 内容包含 @用户名 或 @昵称」查询。
    """
    room_rows = (await db.execute(
        select(GroupMember.room_id).where(GroupMember.user_id == user.id)
    )).all()
    room_ids = [r[0] for r in room_rows]
    if not room_ids:
        return []

    now = datetime.now(timezone.utc)
    since = now - timedelta(days=days)
    patterns = [f"%@{_escape_like(user.username)}%"]
    if user.nickname:
        patterns.append(f"%@{_escape_like(user.nickname)}%")

    rows = (await db.execute(
        select(GroupMessage, User, GroupRoom)
        .join(User, User.id == GroupMessage.sender_id)
        .join(GroupRoom, GroupRoom.id == GroupMessage.room_id)
        .where(
            GroupMessage.room_id.in_(room_ids),
            GroupMessage.sender_id != user.id,
            GroupMessage.is_recalled.is_(False),
            GroupMessage.created_at >= since,
            or_(*[GroupMessage.content.ilike(p) for p in patterns]),
        )
        .order_by(GroupMessage.created_at.desc())
        .limit(limit)
    )).all()

    return [
        {
            "id": str(m.id),
            "content": m.content,
            "seq_id": m.seq_id,
            "created_at": format_utc_iso(m.created_at),
            "sender_username": u.username if u else "",
            "sender_nickname": (u.nickname if u else "") or "",
            "sender_avatar": (u.avatar_url if u else "") or "",
            "room_id": str(room.id),
            "room_name": room.name,
        }
        for m, u, room in rows
    ]


# ==================== 群聊增强：禁言 / 铭牌 / 等级榜 ====================

@router.get("/groups/{room_id}/badges")
async def list_group_badges(
    room_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db),
):
    """20 级默认铭牌（前端按等级渲染对应颜色与发光效果）。"""
    return {
        "names": GROUP_BADGE_NAMES,
        "max_level": GROUP_LEVEL_MAX,
        "step_exp": GROUP_LEVEL_STEP,
        "items": [
            {"level": i + 1, "badge_id": i + 1, "name": GROUP_BADGE_NAMES[i],
             "need_exp": i * GROUP_LEVEL_STEP}
            for i in range(GROUP_LEVEL_MAX)
        ],
    }


async def _require_group_manager(db: AsyncSession, room_id: UUID, user: User):
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")
    member = await get_group_member(db, room_id, user.id)
    if not _is_group_manager(room, user, member):
        raise HTTPException(status_code=403, detail="仅群主或群管理员可执行此操作")
    return room, member


async def _mute_target(db: AsyncSession, room_id: UUID, target_user_id: UUID, operator: User):
    target = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == target_user_id)
    )).scalar_one_or_none()
    if not target:
        raise HTTPException(status_code=404, detail="该成员不在群内")
    if target.user_id == operator.id:
        raise HTTPException(status_code=400, detail="不能对自己操作")
    if target.role == "owner":
        raise HTTPException(status_code=400, detail="不能对群主操作")
    op_member = await get_group_member(db, room_id, operator.id)
    if target.role == "admin" and not (
        _is_site_admin(operator) or (op_member and op_member.role == "owner")
    ):
        raise HTTPException(status_code=403, detail="仅群主可管理其他管理员")
    return target


@router.post("/groups/{room_id}/members/{target_user_id}/mute")
async def mute_group_member(
    room_id: UUID, target_user_id: UUID, req: GroupMuteRequest,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    room, _ = await _require_group_manager(db, room_id, user)
    target = await _mute_target(db, room_id, target_user_id, user)
    target.muted_until = datetime.now(timezone.utc) + timedelta(minutes=req.duration_minutes)
    target.mute_reason = (req.reason or "").strip()[:200] or None
    await db.commit()
    await notify_group_members_changed(db, room_id, room.is_public, "member_muted")
    await ws_manager.send_personal_message({
        "type": "group_muted", "room_id": str(room_id),
        "minutes": req.duration_minutes, "reason": target.mute_reason,
    }, str(target_user_id))
    return {"ok": True, "muted_until": target.muted_until.isoformat()}


@router.post("/groups/{room_id}/members/{target_user_id}/unmute")
async def unmute_group_member(
    room_id: UUID, target_user_id: UUID,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    room, _ = await _require_group_manager(db, room_id, user)
    target = await _mute_target(db, room_id, target_user_id, user)
    target.muted_until = None
    target.mute_reason = None
    await db.commit()
    await notify_group_members_changed(db, room_id, room.is_public, "member_unmuted")
    return {"ok": True}


@router.get("/groups/{room_id}/members/{target_user_id}/special-badges")
async def list_special_badges(
    room_id: UUID, target_user_id: UUID,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    """查看某成员拥有的特殊名牌。本人或群主/管理员可查看。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")
    me = await get_group_member(db, room_id, user.id)
    is_manager = bool(me and getattr(me, "role", "member") in ("owner", "admin"))
    is_super = getattr(user, "role", "") in ("super_admin", "root")
    if str(user.id) != str(target_user_id) and not (is_manager or is_super):
        raise HTTPException(status_code=403, detail="没有权限查看他人的名牌")

    target = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == target_user_id)
    )).scalar_one_or_none()
    rows = (await db.execute(
        select(GroupSpecialBadge)
        .where(GroupSpecialBadge.room_id == room_id, GroupSpecialBadge.user_id == target_user_id)
        .order_by(GroupSpecialBadge.created_at.asc())
    )).scalars().all()
    wearing = (getattr(target, "special_badge_name", None) or "").strip() if target else ""
    return {
        "items": [{
            "id": str(b.id), "name": b.name,
            "worn": bool(wearing) and wearing == b.name,
            "created_at": b.created_at,
        } for b in rows],
        "wearing": wearing or None,
        "group_level": int(getattr(target, "group_level", 1) or 1) if target else 1,
        "level_badge_name": GROUP_BADGE_NAMES[
            max(1, min(GROUP_LEVEL_MAX, int(getattr(target, "group_level", 1) or 1))) - 1
        ] if target else GROUP_BADGE_NAMES[0],
    }


@router.post("/groups/{room_id}/members/{target_user_id}/special-badges")
async def grant_special_badge(
    room_id: UUID, target_user_id: UUID, req: GroupSpecialBadgeRequest,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    """授予特殊名牌（仅群主/管理员，或站长）。一人可拥有多个，可重复授予不同名。"""
    room, _ = await _require_group_manager(db, room_id, user)
    target = (await db.execute(
        select(GroupMember).where(GroupMember.room_id == room_id, GroupMember.user_id == target_user_id)
    )).scalar_one_or_none()
    if not target:
        raise HTTPException(status_code=404, detail="该成员不在群内")

    name = req.name.strip()[:32]
    if not name:
        raise HTTPException(status_code=400, detail="名牌名称不能为空")
    exists = (await db.execute(
        select(GroupSpecialBadge).where(
            GroupSpecialBadge.room_id == room_id,
            GroupSpecialBadge.user_id == target_user_id,
            GroupSpecialBadge.name == name,
        )
    )).scalar_one_or_none()
    if not exists:
        db.add(GroupSpecialBadge(
            id=uuid4(), room_id=room_id, user_id=target_user_id,
            name=name, granted_by=user.id,
        ))
        await db.commit()
    await notify_group_members_changed(db, room_id, room.is_public, "badge_changed")
    return {"ok": True, "name": name, "created": not bool(exists)}


@router.delete("/groups/{room_id}/special-badges/{badge_id}")
async def revoke_special_badge(
    room_id: UUID, badge_id: UUID,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    """收回特殊名牌（仅群主/管理员）。若该成员正佩戴它，自动退回等级铭牌。"""
    room, _ = await _require_group_manager(db, room_id, user)
    row = (await db.execute(
        select(GroupSpecialBadge).where(
            GroupSpecialBadge.id == badge_id, GroupSpecialBadge.room_id == room_id,
        )
    )).scalar_one_or_none()
    if not row:
        raise HTTPException(status_code=404, detail="名牌不存在")
    target = (await db.execute(
        select(GroupMember).where(
            GroupMember.room_id == room_id, GroupMember.user_id == row.user_id,
        )
    )).scalar_one_or_none()
    if target and (getattr(target, "special_badge_name", None) or "").strip() == row.name:
        target.special_badge_name = None
    await db.delete(row)
    await db.commit()
    await notify_group_members_changed(db, room_id, room.is_public, "badge_changed")
    return {"ok": True}


@router.put("/groups/{room_id}/me/badge")
async def wear_my_badge(
    room_id: UUID, req: GroupWearBadgeRequest,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    """本人选择佩戴：special_id=None → 等级铭牌；否则必须是已被授予的名牌。"""
    member = await get_group_member(db, room_id, user.id)
    if not member:
        raise HTTPException(status_code=403, detail="你不在该群内")
    if req.special_id is None:
        member.special_badge_name = None
    else:
        row = (await db.execute(
            select(GroupSpecialBadge).where(
                GroupSpecialBadge.id == req.special_id,
                GroupSpecialBadge.room_id == room_id,
                GroupSpecialBadge.user_id == user.id,
            )
        )).scalar_one_or_none()
        if not row:
            raise HTTPException(status_code=403, detail="该名牌未授予你")
        member.special_badge_name = row.name
    await db.commit()
    await notify_group_members_changed(db, room_id, True, "badge_mode_changed")
    return {
        "ok": True,
        "special_badge_name": member.special_badge_name,
        "badge_name": group_badge_of(member)[1],
    }


@router.get("/groups/{room_id}/levels")
async def group_level_leaderboard(
    room_id: UUID,
    user: User = Depends(get_current_active_user),
    db: AsyncSession = Depends(get_db),
):
    """群聊等级榜（只展示群等级，不展示网站等级）。"""
    rows = (await db.execute(
        select(GroupMember, User.username, User.nickname, User.avatar_url)
        .join(User, User.id == GroupMember.user_id)
        .where(GroupMember.room_id == room_id)
        .order_by(desc(GroupMember.group_exp))
        .limit(20)
    )).all()
    return {"items": [{
        "rank": i + 1, "user_id": str(m.user_id), "username": un,
        "nickname": nn or un, "avatar_url": av or "",
        "group_level": int(getattr(m, "group_level", 1) or 1),
        "group_exp": int(getattr(m, "group_exp", 0) or 0),
        "badge_name": group_badge_of(m)[1],
        "badge_id": group_badge_of(m)[0],
        "badge_kind": "special" if (getattr(m, "special_badge_name", None) or "").strip() else "level",
    } for i, (m, un, nn, av) in enumerate(rows)]}


@router.post("/groups/{room_id}/dice")
async def roll_group_dice(
    room_id: UUID,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    """服务端生成点数：普通文本消息无法伪造这种 msg_type，客户端只收到结果。"""
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")
    member = await get_group_member(db, room_id, user.id)
    mute_err = await _check_group_mute(room, user, member)
    if mute_err:
        raise HTTPException(status_code=403, detail=mute_err)
    points = random.randint(1, 6)
    content = json.dumps({"points": points}, ensure_ascii=False)
    msg = GroupMessage(
        id=uuid4(), room_id=room_id, sender_id=user.id,
        content=content, msg_type="dice", seq_id=await next_seq_id(),
    )
    db.add(msg)
    if member:
        member.last_read_at = datetime.now(timezone.utc)
    await award_group_exp(db, member, content)
    await db.commit()
    await db.refresh(msg)
    payload = {
        "type": "group_msg", "room_id": str(room_id), "msg_id": str(msg.id),
        "sender_id": str(user.id), "sender_username": user.username,
        "sender_nickname": user.nickname, "sender_avatar": user.avatar_url or "",
        "sender_level": user.level, "content": content, "msg_type": "dice",
        "seq_id": msg.seq_id, "is_recalled": False, "is_mention_all": False,
        "created_at": format_utc_iso(msg.created_at),
    }
    await push_group_event(db, room_id, room.is_public, payload, extra_target_id=str(user.id))
    return {"ok": True, "points": points, "msg_id": str(msg.id)}


@router.put("/groups/{room_id}/checkin-config")
async def update_group_checkin_config(
    room_id: UUID, req: GroupCheckinConfigRequest,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    """管理员/群主设置群签到语、每日次数与每次经验。"""
    room, _ = await _require_group_manager(db, room_id, user)
    room.checkin_text = req.text.strip()[:32]
    await db.commit()
    from app.services.level_service import get_system_setting_int
    times = await get_system_setting_int(db, "checkin_times_per_day", 1)
    return {"ok": True, "text": room.checkin_text, "times_per_day": times, "exp": 10}


@router.get("/groups/{room_id}/checkin-status")
async def group_checkin_status(
    room_id: UUID,
    user: User = Depends(get_current_active_user), db: AsyncSession = Depends(get_db),
):
    room = (await db.execute(select(GroupRoom).where(GroupRoom.id == room_id))).scalar_one_or_none()
    if not room:
        raise HTTPException(status_code=404, detail="群聊频道不存在")
    used = int((await db.scalar(
        select(func.count()).select_from(GroupCheckin).where(
            GroupCheckin.room_id == room_id, GroupCheckin.user_id == user.id,
            GroupCheckin.checkin_date == _group_today(),
        )
    )) or 0)
    from app.services.level_service import get_system_setting_int
    times = max(1, min(3, await get_system_setting_int(db, "checkin_times_per_day", 1)))
    return {
        "text": room.checkin_text or "签到",
        "times_per_day": times,
        "exp": 10,
        "used": used, "remaining": max(0, times - used),
    }
