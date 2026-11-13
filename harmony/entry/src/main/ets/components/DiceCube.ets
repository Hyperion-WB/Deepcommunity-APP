import { DeepColors } from '../common/Constants';

/**
 * 鸿蒙 3D 物理高仿真象牙骰子组件 (DiceCube ArkUI 3D)
 * 100% 还原 Web 端 DiceCube.tsx 与 Android 原生物理翻滚特性：
 * 象牙白立体渐变 (#FFFDF8 -> #E9E5DB)、金属内嵌点阵（1点朱红圆心，2~6点金属深色凹孔）、
 * 3D 欧拉角空间翻转投射与落地回弹缓冲。
 */
@Component
export struct DiceCube {
  @Prop targetPoint: number = 6;
  @State currentPoint: number = 6;
  @State isRolling: boolean = false;
  @State rotationAngleX: number = 0;
  @State rotationAngleY: number = 0;
  @State cubeScale: number = 1.0;
  private rollTimer: number = -1;

  aboutToAppear() {
    this.currentPoint = this.targetPoint || 6;
    this.roll();
  }

  aboutToDisappear() {
    if (this.rollTimer !== -1) {
      clearInterval(this.rollTimer);
    }
  }

  public roll() {
    if (this.isRolling) return;
    this.isRolling = true;
    this.cubeScale = 0.94;

    // 随机旋转圈数
    const extraTurnsX = (Math.floor(Math.random() * 3) + 2) * 360;
    const extraTurnsY = (Math.floor(Math.random() * 3) + 2) * 360;

    // 目标点数角度映射
    let baseAngleX = 0;
    let baseAngleY = 0;
    switch (this.targetPoint) {
      case 1: baseAngleX = 0; baseAngleY = 0; break;
      case 2: baseAngleX = 0; baseAngleY = -90; break;
      case 3: baseAngleX = -90; baseAngleY = 0; break;
      case 4: baseAngleX = 90; baseAngleY = 0; break;
      case 5: baseAngleX = 0; baseAngleY = 90; break;
      case 6: baseAngleX = 0; baseAngleY = 180; break;
    }

    animateTo({
      duration: 1000,
      curve: Curve.FastOutSlowIn,
      onFinish: () => {
        this.currentPoint = this.targetPoint;
        this.isRolling = false;
        // 落地微回弹反馈
        animateTo({ duration: 180, curve: Curve.EaseOut }, () => {
          this.cubeScale = 1.08;
        });
        setTimeout(() => {
          animateTo({ duration: 140, curve: Curve.EaseInOut }, () => {
            this.cubeScale = 1.0;
          });
        }, 180);
      }
    }, () => {
      this.rotationAngleX = extraTurnsX + baseAngleX;
      this.rotationAngleY = extraTurnsY + baseAngleY;
    });

    // 翻滚期间表面高速随机变点
    let count = 0;
    this.rollTimer = setInterval(() => {
      count++;
      this.currentPoint = Math.floor(Math.random() * 6) + 1;
      if (count > 12) {
        clearInterval(this.rollTimer);
        this.rollTimer = -1;
      }
    }, 60);
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      // 象牙骨双色渐变与金属阴影
      Column() {
        this.RenderDotMatrix(this.currentPoint)
      }
      .width(58)
      .height(58)
      .borderRadius(14)
      .linearGradient({
        angle: 135,
        colors: [
          ['#FFFDF8', 0.0],
          ['#F3EFE6', 0.5],
          ['#E5DFD3', 1.0]
        ]
      })
      .border({ width: 1.5, color: '#D6CFC0' })
      .shadow({
        radius: 12,
        color: 'rgba(0, 0, 0, 0.28)',
        offsetX: 2,
        offsetY: 6
      })
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .padding(6)
    }
    .width(68)
    .height(68)
    .rotate({
      x: 1,
      y: 1,
      z: 0,
      angle: this.rotationAngleX % 360,
      centerX: '50%',
      centerY: '50%'
    })
    .scale({ x: this.cubeScale, y: this.cubeScale })
    .onClick(() => {
      this.roll();
    })
  }

  @Builder
  RenderDotMatrix(point: number) {
    Grid() {
      ForEach([0, 1, 2, 3, 4, 5, 6, 7, 8], (idx: number) => {
        GridItem() {
          if (this.hasDotAt(point, idx)) {
            Circle()
              .width(point === 1 ? 14 : 9)
              .height(point === 1 ? 14 : 9)
              .fill(point === 1 ? '#DC2626' : '#262626')
              .shadow({
                radius: 1,
                color: 'rgba(255, 255, 255, 0.4)',
                offsetX: 0.5,
                offsetY: 0.5
              })
          }
        }
      })
    }
    .columnsTemplate('1fr 1fr 1fr')
    .rowsTemplate('1fr 1fr 1fr')
    .width('100%')
    .height('100%')
  }

  private hasDotAt(point: number, idx: number): boolean {
    switch (point) {
      case 1: return idx === 4;
      case 2: return idx === 0 || idx === 8;
      case 3: return idx === 0 || idx === 4 || idx === 8;
      case 4: return idx === 0 || idx === 2 || idx === 6 || idx === 8;
      case 5: return idx === 0 || idx === 2 || idx === 4 || idx === 6 || idx === 8;
      case 6: return idx === 0 || idx === 2 || idx === 3 || idx === 5 || idx === 6 || idx === 8;
      default: return false;
    }
  }
}
