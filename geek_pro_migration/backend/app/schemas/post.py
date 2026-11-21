from typing import Optional, List
from uuid import UUID
from datetime import datetime
from pydantic import BaseModel, Field, field_validator
from app.schemas.interaction import PollDetailResponse

class TOCItem(BaseModel):
    id: str
    text: str
    level: int

class PollCreateInPost(BaseModel):
    title: str = Field(..., min_length=2, max_length=200)
    # 此前无任何约束：选项数量/长度不限（超 100 字符直接 DB 500）、时长可为负（创建即过期）
    options: List[str] = Field(..., min_length=2, max_length=8)
    is_multiple: bool = False
    max_choices: int = Field(1, ge=1, le=8)
    duration_days: int = Field(7, ge=1, le=365)

    @field_validator("options")
    @classmethod
    def _check_options(cls, v: List[str]) -> List[str]:
        cleaned = [o.strip() for o in v if o and o.strip()]
        if len(cleaned) < 2:
            raise ValueError("投票至少需要 2 个有效选项")
        for o in cleaned:
            if len(o) > 100:
                raise ValueError("单个投票选项不能超过 100 个字符")
        return cleaned

class PostCreateRequest(BaseModel):
    title: Optional[str] = Field(None, max_length=200)
    content_type: str = Field("markdown", pattern="^(markdown|plain)$")
    # 好文分享板块专用
    source_type: Optional[str] = Field(None, pattern="^(original|repost)$")
    source_note: Optional[str] = Field(None, max_length=500)
    # 仅好文分享页：用户勾选「我已知晓」承诺书
    source_ack: Optional[bool] = None
    content_md: str = Field(..., min_length=1, max_length=200000)
    category_id: Optional[int] = None
    cover_image: Optional[str] = None
    # 九宫格多图（最多 10 张，按顺序）
    image_urls: Optional[List[str]] = Field(default=None, max_length=10)
    post_type: str = Field("post", pattern=r"^(post|news)$") # post / news
    status: str = Field("published", pattern=r"^(draft|scheduled|published)$")
    comment_policy: str = Field("open", pattern=r"^(open|member_only|closed)$")
    tags: Optional[List[str]] = Field(default=[], max_length=10)
    scheduled_at: Optional[datetime] = None
    poll: Optional[PollCreateInPost] = None

    @field_validator("tags")
    @classmethod
    def _check_tags(cls, v: Optional[List[str]]) -> Optional[List[str]]:
        if v:
            for t in v:
                if len(t.strip()) > 30:
                    raise ValueError("单个标签不能超过 30 个字符")
        return v

class PostUpdateRequest(BaseModel):
    title: Optional[str] = Field(None, min_length=1, max_length=200)
    content_type: str = Field("markdown", pattern="^(markdown|plain)$")
    content_md: Optional[str] = Field(None, min_length=1, max_length=200000)
    category_id: Optional[int] = None
    cover_image: Optional[str] = None
    image_urls: Optional[List[str]] = Field(default=None, max_length=10)
    comment_policy: Optional[str] = Field(None, pattern=r"^(open|member_only|closed)$")
    tags: Optional[List[str]] = Field(default=None, max_length=10)
    status: Optional[str] = Field(None, pattern=r"^(published|draft|scheduled)$")
    scheduled_at: Optional[datetime] = None
    poll: Optional[PollCreateInPost] = None

    @field_validator("tags")
    @classmethod
    def _check_tags(cls, v: Optional[List[str]]) -> Optional[List[str]]:
        if v:
            for t in v:
                if len(t.strip()) > 30:
                    raise ValueError("单个标签不能超过 30 个字符")
        return v

class PostMoveCategoryRequest(BaseModel):
    """换区请求：把帖子迁移到另一个板块"""
    category_id: int = Field(..., ge=1, description="目标板块 ID")

class BatchStatusRequest(BaseModel):
    post_ids: List[str] = Field(default=[], max_length=100)

class PostSimpleResponse(BaseModel):
    id: UUID
    group_name: Optional[str] = None
    title: str
    post_type: str
    status: str
    is_pinned: bool
    is_essence: bool = False
    view_count: int
    like_count: int
    comment_count: int
    cover_image: Optional[str] = None
    # 九宫格多图
    image_urls: Optional[List[str]] = None
    # 列表展示图：优先用户设置的封面，没有则取正文第一张插图
    display_image: Optional[str] = None
    created_at: datetime
    reading_time_minutes: int = 1
    author_id: Optional[UUID] = None
    author_username: str
    author_nickname: Optional[str] = None
    author_avatar: Optional[str] = None
    author_level: int = 1
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    tags: List[str] = []
    poll_id: Optional[UUID] = None
    summary: Optional[str] = None
    # 搜索命中摘要（仅带 q 参数时返回，用于说明正文/标签为何命中）
    match_snippet: Optional[str] = None

class PostDetailResponse(PostSimpleResponse):
    source_type: Optional[str] = None
    source_note: Optional[str] = None
    content_type: str = "markdown"
    content_md: str
    content_html: str
    comment_policy: str
    is_liked: bool = False
    is_collected: bool = False
    accepted_comment_id: Optional[UUID] = None
    toc: List[TOCItem] = []
    edits_count: int = 0
    poll: Optional[PollDetailResponse] = None
    # 作者在线状态（90 秒心跳）：「作者名片」与头像绿点使用；对方隐藏在线状态时恒为 False
    author_is_online: bool = False

class PostDraftRequest(BaseModel):
    draft_id: Optional[UUID] = None
    content_type: str = Field("markdown", pattern="^(markdown|plain)$")
    title: Optional[str] = Field("", max_length=200)
    content_md: Optional[str] = Field("", max_length=200000)
    category_id: Optional[int] = None
    post_type: Optional[str] = Field("post", pattern=r"^(post|news)$")
    tags: Optional[List[str]] = Field(default=[], max_length=10)
    # 草稿也要保留九宫格相册，否则「上传图片 → 自动存草稿 → 发布」会把图丢掉
    image_urls: Optional[List[str]] = Field(default=None, max_length=10)
    # 草稿也要保留评论区权限与定时发布时间
    comment_policy: Optional[str] = Field("open", pattern=r"^(open|member_only|closed)$")
    scheduled_at: Optional[datetime] = None

class PostSeriesCreateRequest(BaseModel):
    title: str = Field(..., min_length=2, max_length=100)
    description: Optional[str] = Field("", max_length=500)
    cover_image: Optional[str] = None

class PostSeriesItemAddRequest(BaseModel):
    post_id: UUID
    sort_order: Optional[int] = 0

class SeriesItemResponse(BaseModel):
    id: int
    post_id: UUID
    post_title: str
    sort_order: int

class PostSeriesResponse(BaseModel):
    id: UUID
    author_id: UUID
    author_username: str
    author_nickname: Optional[str] = None
    title: str
    description: str
    cover_image: Optional[str] = None
    created_at: datetime
    chapter_count: int = 0
    items: List[SeriesItemResponse] = []


