package p26x29;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;

import java.util.stream.IntStream;

import ap26.Board;

/**
 * 課題 1d 用。マス重み行列を調整した評価関数（黒視点）。
 *
 * <p>元の {@link MyEval} は外周（角・辺）を一律 10 とし、角と辺を区別して
 * いなかった。本クラスでは次の経験則に基づき重みを細分化した。
 * <ul>
 *   <li>角 (a1,f1,a6,f6) は一度取れば二度と返されない最重要マス → 大きな正</li>
 *   <li>X マス (b2 等, 角の斜め隣) は相手に角を与えやすい → 強い負</li>
 *   <li>C マス (a2,b1 等, 角の隣の辺) も角を渡しやすい → 弱い負</li>
 *   <li>辺の中央寄りは安定しやすい → 中程度の正</li>
 *   <li>中央 4 マスは早期に占めても価値が低い → 小さな正</li>
 * </ul>
 */
public class ImprovedEval extends MyEval {

  /** 調整後の重み行列（6×6, 黒視点）。 */
  static final float[][] M2 = {
      { 45, -3, 11, 11, -3, 45 },
      { -3, -7, -4, -4, -7, -3 },
      { 11, -4,  2,  2, -4, 11 },
      { 11, -4,  2,  2, -4, 11 },
      { -3, -7, -4, -4, -7, -3 },
      { 45, -3, 11, 11, -3, 45 },
  };

  @Override
  public float value(Board board) {
    if (board.isEnd()) {
      return 1_000_000 * board.score();
    }
    return (float) IntStream.range(0, LENGTH)
        .mapToDouble(k -> M2[k / SIZE][k % SIZE] * board.get(k).getValue())
        .sum();
  }
}
