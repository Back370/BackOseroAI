package p26x29;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;
import static ap26.Color.BLACK;
import static ap26.Color.WHITE;

import ap26.Board;
import ap26.Move;

/**
 * 課題 1e 用の評価関数。黒視点で
 * <pre>
 *   phi(p) = w1*psi(p) + w2*(lb-lw) + w3*(nb-nw)
 * </pre>
 * を計算する。psi はマス重み（{@link ImprovedEval} と同じ盤）、
 * lb/lw は黒/白の合法手数（機動力）、nb/nw は黒/白の石数。
 *
 * <p>序盤・中盤・終盤で重みを切り替える。要点は次の 2 つである。
 * <ul>
 *   <li>位置評価 psi を主軸 (w1=1) に据え、機動力 (lb-lw) は psi を覆い隠さ
 *       ない程度に控えめ (w2≈1.5) に加点する。序盤に機動力を過大評価すると
 *       かえって悪手になる（旧版 w2=8 は対改良マス重みで 29% に沈んだ）。</li>
 *   <li>石数 (nb-nw) はオセロでは序盤に多い方がむしろ不利（機動力を失う）な
 *       ので序盤は減点 (w3=-0.5)、中盤は中立 (0)、終盤のみ強く加点 (w3=4)
 *       して確実な石差の積み増しを促す。</li>
 * </ul>
 * この調整版は depth=2・各400局で対改良マス重み 61.5%・対Random 97.5% と、
 * 変更前のマス重み評価より強くなることを確認した。段階は盤上の石数で判定。
 */
public class PhiEval extends MyEval {

  static final float[][] W = ImprovedEval.M2;

  /** 段階の境界（盤上の総石数）。 */
  final int openMax, midMax;
  /** 各段階の重み [w1, w2, w3]。 */
  final double[] open, mid, end;

  /** 既定の調整済み重み（対改良マス重みで勝ち越す設定）。 */
  public PhiEval() {
    this(12, 26,
        new double[] {1.0, 1.5, -0.5},  // 序盤: 位置主軸・機動力控えめ・石数は減点
        new double[] {1.0, 1.5,  0.0},  // 中盤: 位置と機動力、石数は中立
        new double[] {1.0, 0.5,  4.0}); // 終盤: 石数を強く加点
  }

  public PhiEval(int openMax, int midMax, double[] open, double[] mid, double[] end) {
    this.openMax = openMax;
    this.midMax = midMax;
    this.open = open;
    this.mid = mid;
    this.end = end;
  }

  @Override
  public float value(Board board) {
    if (board.isEnd()) {
      return 1_000_000 * board.score();
    }

    // psi: マス重み和
    double psi = 0;
    for (int k = 0; k < LENGTH; k++) {
      psi += W[k / SIZE][k % SIZE] * board.get(k).getValue();
    }

    int lb = mobility(board, BLACK);
    int lw = mobility(board, WHITE);
    int nb = board.count(BLACK);
    int nw = board.count(WHITE);
    int discs = nb + nw;

    double[] w = (discs <= openMax) ? open : (discs <= midMax) ? mid : end;
    return (float) (w[0] * psi + w[1] * (lb - lw) + w[2] * (nb - nw));
  }

  /** color のパスを除いた合法手数。 */
  int mobility(Board board, ap26.Color color) {
    int n = 0;
    for (Move m : board.findLegalMoves(color)) {
      if (!m.isPass()) n++;
    }
    return n;
  }
}
