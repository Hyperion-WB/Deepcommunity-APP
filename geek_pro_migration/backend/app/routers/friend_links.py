"""友情链接：前台展示 + 站长管理。

设计取舍：
- 图标一律由后端抓取或站长上传后**本地化存储**，前台只引用本站地址，
  避免把访客 IP 暴露给对方站点，也避免对方防盗链导致裂图；
- 抓取失败不阻断录入，icon_status 标记为 failed，前端用「站名首字 + 色块」兜底；
- status 字段为后续「他人申请 → 审核」预留，本期站长添加即 approved。
"""

import io
import os
import re
import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import RedirectResponse
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db, get_redis
from app.core.timezone import utc_now
from app.models.friend_link import FriendLink
from app.models.user import User
from app.routers.deps import get_current_root_admin, get_current_user
from app.services.image_service import save_upload_image
from app.services.link_icon_service import UnsafeUrlError, fetch_site_icon, normalize_url

router = APIRouter(prefix="/links", tags=["friend-links"])
admin_router = APIRouter(prefix="/admin/links", tags=["friend-links-admin"])


class FriendLinkOut(BaseModel):
    id: uuid.UUID
    site_name: str
    url: str
    description: str
    icon_url: str
    icon_status: str
    status: str = "approved"
    sort_order: int
    visible: bool
    click_count: int = 0
    applicant_contact: str = ""
    reject_reason: str = ""

    class Config:
        from_attributes = True


class LinkApplyRequest(BaseModel):
    site_name: str = Field(..., min_length=1, max_length=64)
    url: str = Field(..., min_length=4, max_length=512)
    description: str = Field("", max_length=200)
    contact: str = Field("", max_length=120)


class LinkApproveRequest(BaseModel):
    """审核通过时可顺手改写文案与排序（站长有最终编辑权）"""
    description: Optional[str] = Field(None, max_length=200)
    sort_order: Optional[int] = None


class LinkRejectRequest(BaseModel):
    reason: str = Field("", max_length=200)


class FriendLinkCreate(BaseModel):
    site_name: str = Field(..., min_length=1, max_length=64)
    url: str = Field(..., min_length=4, max_length=512)
    description: str = Field("", max_length=200)
    sort_order: int = 0
    visible: bool = True
    # 添加后立即尝试抓取图标（失败不影响保存）
    auto_icon: bool = True
    # 站长手动上传图标时直接带上（优先于自动抓取）
    icon_url: Optional[str] = Field(None, max_length=512)


class FriendLinkUpdate(BaseModel):
    site_name: Optional[str] = Field(None, min_length=1, max_length=64)
    url: Optional[str] = Field(None, min_length=4, max_length=512)
    description: Optional[str] = Field(None, max_length=200)
    sort_order: Optional[int] = None
    visible: Optional[bool] = None
    # 手动更换图标
    icon_url: Optional[str] = Field(None, max_length=512)


MAX_SVG_BYTES = 256 * 1024


def _sanitize_svg(raw: bytes) -> Optional[bytes]:
    """清理 SVG：只保留绘图内容。

    友链图标常见 svg，但 SVG 可以内嵌脚本；存到同域后又可能被直接打开，
    因此入库前必须剥掉 script / foreignObject / on* 事件 / javascript: 链接。
    """
    try:
        text = raw.decode("utf-8", "ignore")
    except Exception:
        return None
    if "<svg" not in text.lower():
        return None
    text = re.sub(r"<script[\s\S]*?</script>", "", text, flags=re.I)
    text = re.sub(r"<foreignObject[\s\S]*?</foreignObject>", "", text, flags=re.I)
    text = re.sub(r"\son\w+\s*=\s*(\"[^\"]*\"|'[^']*'|[^\s>]+)", "", text, flags=re.I)
    text = re.sub(r"(href|xlink:href)\s*=\s*[\"']\s*javascript:[^\"']*[\"']", "", text, flags=re.I)
    cleaned = text.encode("utf-8")
    if len(cleaned) > MAX_SVG_BYTES:
        return None
    return cleaned


async def _store_icon_bytes(data: bytes, uploader_id: str) -> tuple[str, str]:
    """把图标存成本地文件，返回 (url, status)。

    普通位图走统一压缩链路；ICO 先转 PNG；SVG 清理后原样保存
    （Pillow 不解析 SVG，而很多站点只提供 svg 图标）。
    """
    # 1) png / jpg / webp / gif：复用现有上传链路（含 S3 镜像与压缩）
    try:
        upload = UploadFile(file=io.BytesIO(data), filename="friend-link-icon.png")
        url, _size = await save_upload_image(upload, uploader_id, is_avatar=True)
        return url, "ok"
    except Exception:
        pass

    # 2) ICO：Pillow 能解码，转成 PNG 后再走统一链路
    try:
        from PIL import Image

        with Image.open(io.BytesIO(data)) as img:
            buf = io.BytesIO()
            img.convert("RGBA").save(buf, format="PNG")
            png_bytes = buf.getvalue()
        upload = UploadFile(file=io.BytesIO(png_bytes), filename="friend-link-icon.png")
        url, _size = await save_upload_image(upload, uploader_id, is_avatar=True)
        return url, "ok"
    except Exception:
        pass

    # 3) SVG：清理后存本地
    svg = _sanitize_svg(data)
    if svg:
        try:
            rel_dir = os.path.join(settings.UPLOAD_DIR, "links")
            os.makedirs(rel_dir, exist_ok=True)
            name = f"{uuid.uuid4().hex}.svg"
            with open(os.path.join(rel_dir, name), "wb") as f:
                f.write(svg)
            return f"/uploads/links/{name}", "ok"
        except Exception as exc:
            print(f"[FriendLink] SVG 保存失败: {str(exc)[:120]}")

    print("[FriendLink] 图标无法识别（既非位图也非合法 SVG）")
    return "", "failed"


async def _try_fetch_icon(site_url: str, uploader_id: str) -> tuple[str, str]:
    """抓取站点图标；任何失败都降级为 failed，不抛给调用方。"""
    try:
        data = await fetch_site_icon(site_url)
    except UnsafeUrlError:
        raise
    except Exception as exc:
        print(f"[FriendLink] 图标抓取异常: {str(exc)[:120]}")
        return "", "failed"

    if not data:
        return "", "failed"
    return await _store_icon_bytes(data, uploader_id)


@router.get("", response_model=List[FriendLinkOut])
async def list_public_links(db: AsyncSession = Depends(get_db)):
    """前台展示用：只返回已上架且可见的友链。"""
    rows = (
        await db.execute(
            select(FriendLink)
            .where(FriendLink.visible.is_(True), FriendLink.status == "approved")
            .order_by(FriendLink.sort_order.asc(), FriendLink.created_at.asc())
        )
    ).scalars().all()
    return rows


LINK_APPLY_DAILY_LIMIT = 3


@router.post("/apply", response_model=FriendLinkOut)
async def apply_link(
    payload: LinkApplyRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> FriendLink:
    """用户申请友链：入库为 pending，等站长审核。

    限频防止有人刷申请把后台塞满；同一个网址不允许重复申请。
    """
    redis = get_redis()
    if redis:
        try:
            key = f"linkapply:{user.id}:{utc_now().strftime('%Y%m%d')}"
            used = await redis.incr(key)
            if used == 1:
                await redis.expire(key, 86400)
            if used > LINK_APPLY_DAILY_LIMIT:
                raise HTTPException(status_code=429, detail="今天提交的次数有点多，明天再来吧")
        except HTTPException:
            raise
        except Exception:
            pass

    try:
        url = normalize_url(payload.url)
    except UnsafeUrlError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    dup = (
        await db.execute(
            select(FriendLink).where(
                FriendLink.url == url, FriendLink.status.in_(("approved", "pending"))
            )
        )
    ).scalar_one_or_none()
    if dup:
        raise HTTPException(status_code=400, detail="这个网址已经在列表里或正在审核中")

    icon_url, icon_status = "", "pending"
    try:
        icon_url, icon_status = await _try_fetch_icon(url, str(user.id))
    except UnsafeUrlError:
        icon_status = "failed"

    link = FriendLink(
        site_name=payload.site_name.strip(),
        url=url,
        description=payload.description.strip(),
        icon_url=icon_url,
        icon_status=icon_status,
        status="pending",
        visible=False,
        applicant_user_id=user.id,
        applicant_contact=payload.contact.strip(),
        icon_checked_at=utc_now(),
    )
    db.add(link)
    await db.commit()
    await db.refresh(link)
    return link


@router.get("/{link_id}/visit")
async def visit_link(link_id: uuid.UUID, db: AsyncSession = Depends(get_db)):
    """点击跳转并计数：前台链接指向这里，302 到目标站点。"""
    link = (
        await db.execute(
            select(FriendLink).where(
                FriendLink.id == link_id,
                FriendLink.status == "approved",
                FriendLink.visible.is_(True),
            )
        )
    ).scalar_one_or_none()
    if not link:
        raise HTTPException(status_code=404, detail="链接不存在或已下架")

    link.click_count = (link.click_count or 0) + 1
    await db.commit()
    return RedirectResponse(url=link.url, status_code=302)


@admin_router.get("", response_model=List[FriendLinkOut])
async def list_all_links(
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    rows = (
        await db.execute(
            select(FriendLink).order_by(FriendLink.sort_order.asc(), FriendLink.created_at.asc())
        )
    ).scalars().all()
    return rows


@admin_router.post("", response_model=FriendLinkOut)
async def create_link(
    payload: FriendLinkCreate,
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    try:
        url = normalize_url(payload.url)
    except UnsafeUrlError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    exists = (
        await db.execute(select(FriendLink).where(FriendLink.url == url))
    ).scalar_one_or_none()
    if exists:
        raise HTTPException(status_code=400, detail="该网址已在友情链接中")

    icon_url, icon_status = "", "pending"
    if payload.icon_url:
        # 手动上传的图标：直接用，不再抓取
        icon_url, icon_status = payload.icon_url, "ok"
    elif payload.auto_icon:
        try:
            icon_url, icon_status = await _try_fetch_icon(url, str(admin.id))
        except UnsafeUrlError as exc:
            # 抓取被安全策略拒绝不影响录入，允许站长后续手动上传图标
            print(f"[FriendLink] 拒绝抓取 {url}: {exc}")
            icon_status = "failed"

    link = FriendLink(
        site_name=payload.site_name.strip(),
        url=url,
        description=payload.description.strip(),
        icon_url=icon_url,
        icon_status=icon_status,
        sort_order=payload.sort_order,
        visible=payload.visible,
        status="approved",
        icon_checked_at=utc_now(),
    )
    db.add(link)
    await db.commit()
    await db.refresh(link)
    return link


@admin_router.put("/{link_id}", response_model=FriendLinkOut)
async def update_link(
    link_id: uuid.UUID,
    payload: FriendLinkUpdate,
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    link = (await db.execute(select(FriendLink).where(FriendLink.id == link_id))).scalar_one_or_none()
    if not link:
        raise HTTPException(status_code=404, detail="友情链接不存在")

    if payload.url is not None:
        try:
            new_url = normalize_url(payload.url)
        except UnsafeUrlError as exc:
            raise HTTPException(status_code=400, detail=str(exc))
        if new_url != link.url:
            dup = (
                await db.execute(select(FriendLink).where(FriendLink.url == new_url))
            ).scalar_one_or_none()
            if dup:
                raise HTTPException(status_code=400, detail="该网址已在友情链接中")
            link.url = new_url
            # 换了网址，旧图标不再对应，标记为待更新
            link.icon_status = "pending"

    if payload.site_name is not None:
        link.site_name = payload.site_name.strip()
    if payload.description is not None:
        link.description = payload.description.strip()
    if payload.sort_order is not None:
        link.sort_order = payload.sort_order
    if payload.visible is not None:
        link.visible = payload.visible
    if payload.icon_url is not None:
        link.icon_url = payload.icon_url
        link.icon_status = "ok" if payload.icon_url else "pending"

    await db.commit()
    await db.refresh(link)
    return link


@admin_router.post("/{link_id}/approve", response_model=FriendLinkOut)
async def approve_link(
    link_id: uuid.UUID,
    payload: LinkApproveRequest,
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    """审核通过：上架并清空拒绝原因，可顺手改写简介与排序。"""
    link = (await db.execute(select(FriendLink).where(FriendLink.id == link_id))).scalar_one_or_none()
    if not link:
        raise HTTPException(status_code=404, detail="友情链接不存在")

    link.status = "approved"
    link.visible = True
    link.reject_reason = ""
    if payload.description is not None:
        link.description = payload.description.strip()
    if payload.sort_order is not None:
        link.sort_order = payload.sort_order

    await db.commit()
    await db.refresh(link)
    return link


@admin_router.post("/{link_id}/reject", response_model=FriendLinkOut)
async def reject_link(
    link_id: uuid.UUID,
    payload: LinkRejectRequest,
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    link = (await db.execute(select(FriendLink).where(FriendLink.id == link_id))).scalar_one_or_none()
    if not link:
        raise HTTPException(status_code=404, detail="友情链接不存在")

    link.status = "rejected"
    link.visible = False
    link.reject_reason = payload.reason.strip()[:200]

    await db.commit()
    await db.refresh(link)
    return link


@admin_router.delete("/{link_id}")
async def delete_link(
    link_id: uuid.UUID,
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    link = (await db.execute(select(FriendLink).where(FriendLink.id == link_id))).scalar_one_or_none()
    if not link:
        raise HTTPException(status_code=404, detail="友情链接不存在")
    await db.delete(link)
    await db.commit()
    return {"message": "已删除"}


@admin_router.post("/{link_id}/refetch-icon", response_model=FriendLinkOut)
async def refetch_icon(
    link_id: uuid.UUID,
    admin: User = Depends(get_current_root_admin),
    db: AsyncSession = Depends(get_db),
):
    link = (await db.execute(select(FriendLink).where(FriendLink.id == link_id))).scalar_one_or_none()
    if not link:
        raise HTTPException(status_code=404, detail="友情链接不存在")

    try:
        icon_url, icon_status = await _try_fetch_icon(link.url, str(admin.id))
    except UnsafeUrlError as exc:
        raise HTTPException(status_code=400, detail=f"该地址不可抓取：{exc}")

    link.icon_url = icon_url
    link.icon_status = icon_status
    await db.commit()
    await db.refresh(link)
    if icon_status != "ok":
        raise HTTPException(status_code=400, detail="没能抓到图标，可以手动上传一张")
    return link


async def refresh_missing_icons(limit: int = 20) -> int:
    """定时任务入口：给「没有图标」的已上架友链重试抓取。

    很多站点在添加时抓取失败（临时 502/DNS 抖动），过段时间就恢复了；
    反复失败也无所谓，只是每天多打几个请求，因此只处理 icon_status != ok 的。
    """
    from app.core.database import AsyncSessionLocal

    fixed = 0
    async with AsyncSessionLocal() as db:
        rows = (
            await db.execute(
                select(FriendLink)
                .where(FriendLink.status == "approved", FriendLink.icon_status != "ok")
                .order_by(FriendLink.created_at.asc())
                .limit(limit)
            )
        ).scalars().all()

        for link in rows:
            try:
                icon_url, status = await _try_fetch_icon(link.url, "system")
            except Exception:
                icon_url, status = "", "failed"
            link.icon_checked_at = utc_now()
            if icon_url:
                link.icon_url = icon_url
                link.icon_status = status
                fixed += 1
            await db.commit()

    return fixed


@admin_router.post("/upload-icon")
async def upload_icon(
    file: UploadFile = File(...),
    admin: User = Depends(get_current_root_admin),
) -> Dict[str, Any]:
    """手动上传图标：有些站点没有 favicon，或自动抓到的很丑。"""
    icon_url, icon_status = await _store_icon_bytes(await file.read(), str(admin.id))
    if icon_status != "ok":
        raise HTTPException(status_code=400, detail="图片无法解析，请换一张 png/jpg/webp/ico")
    return {"icon_url": icon_url, "icon_status": icon_status}
