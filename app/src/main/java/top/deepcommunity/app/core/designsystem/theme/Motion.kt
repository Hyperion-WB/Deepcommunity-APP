package top.deepcommunity.app.core.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 社区动效物理规范
 * 彻底告别生硬线性，全面采用物理手感弹簧（Spring）
 */
object DeepMotion {
    // 温和弹簧：适合页面滑入、底部抽屉展开
    fun <T> gentle(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 250f
    )

    // 弹性果冻弹簧：适合图标点击、按钮回弹、点赞跳跃
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium
    )

    // 迅速清脆弹簧：适合 Tab 切换、小圆点位移
    fun <T> snappy(): SpringSpec<T> = spring(
        dampingRatio = 0.75f,
        stiffness = 600f
    )

    // 抖动摇晃弹簧：适合校验失败、长按微振反馈
    fun <T> wobble(): SpringSpec<T> = spring(
        dampingRatio = 0.35f,
        stiffness = 150f
    )
}
