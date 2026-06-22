package p26x29;

import static ap26.Board.LENGTH;
import static ap26.Color.BLACK;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import ap26.Board;
import ap26.Color;
import ap26.Move;

/**
 * 課題 1g 用。NegaScout（Principal Variation Search, PVS）に
 * <b>置換表（{@link TranspositionTable}）付き move ordering</b> を組み合わせた
 * プレイヤー。
 *
 * <h2>negamax 形式への統一</h2>
 * 既存 {@link MyPlayer} は max 側・min 側を別メソッドに分け、評価関数が黒視点で
 * 書かれている性質を使って「常に黒視点で探索」していた。本クラスはこれを
 * <b>negamax</b> に書き換える。各ノードで
 * <ol>
 *   <li>常に黒の合法手を生成し、黒の手として着手する</li>
 *   <li>{@link Board#flipped()} で盤面を反転して手番を相手に渡す</li>
 *   <li>窓と評価値の符号を反転して再帰する（{@code v = -search(child, -β, -α)}）</li>
 * </ol>
 * とすることで、max/min の重複コードを 1 メソッドに集約できる。盤面反転に
 * よって「手番側の石は常に黒」になるため、黒視点の評価関数 {@link MyEval}
 * がそのまま「手番側から見た評価値」になり、negamax と整合する。
 *
 * <h2>NegaScout（PVS）</h2>
 * 最初の手（move ordering で最有力とみなした手）だけを通常の窓 (α,β) で
 * 探索し、2 番目以降は幅 0 の<b>ヌル窓</b> (α, α+ε) で「α を超えるか否か」だけを
 * 高速に判定する。ヌル窓探索が α を超えた（fail-high）ときのみ通常窓で
 * 探索し直す。move ordering が良ければ再探索はほとんど起きず、α-β より
 * さらにノードを削減できる。
 *
 * <h2>置換表付き move ordering</h2>
 * 各ノードで
 * <ul>
 *   <li>置換表にヒットすれば、深さが足りる場合は値で枝刈り、足りなくても
 *       保存された<b>最善手を最優先</b>で試す（PVS のヌル窓が効きやすくなる）</li>
 *   <li>それ以外の手は「1 手読みの評価値」で降順に並べる（課題 1f と同方針）</li>
 * </ul>
 * という順序付けを行う。
 *
 * <p>{@code useTransposition} / {@code useScout} のフラグで置換表・NegaScout を
 * 個別に無効化でき、純 α-β → 置換表付き → NegaScout の効果を段階的に比較できる
 * （{@link Bench} 実験 g）。
 */
public class NegaScoutPlayer extends MyPlayer {

  static final double NEG_INF = Double.NEGATIVE_INFINITY;
  static final double POS_INF = Double.POSITIVE_INFINITY;

  /**
   * ヌル窓の幅 ε。評価値の最小刻みは 0.5（{@link PhiEval} の終盤重み）なので、
   * それより十分小さく取れば「α を真に超えるか」を正しく判定できる。
   */
  static final double EPS = 1e-3;

  /** ゼロブリストハッシュ用の乱数表。[マス][色 ordinal]。固定 seed で再現性確保。 */
  static final long[][] ZOB = new long[LENGTH][4];
  static {
    Random r = new Random(20260622L);
    for (int k = 0; k < LENGTH; k++)
      for (int c = 0; c < 4; c++) ZOB[k][c] = r.nextLong();
  }

  final boolean useTransposition;
  final boolean useScout;
  final TranspositionTable tt;

  /** 直近の探索で訪れたノード数（nps・枝刈り効果の計測用）。 */
  public long nodes;

  public NegaScoutPlayer(String name, Color color, MyEval eval, int depthLimit) {
    this(name, color, eval, depthLimit, true, true);
  }

  public NegaScoutPlayer(String name, Color color, MyEval eval, int depthLimit,
      boolean useTransposition, boolean useScout) {
    super(name, color, eval, depthLimit);
    this.useTransposition = useTransposition;
    this.useScout = useScout;
    this.tt = useTransposition ? new TranspositionTable(20) : null;
  }

  /** 盤面のゼロブリストハッシュ。探索は常に黒手番なので手番ビットは不要。 */
  long zobrist(MyBoard b) {
    long h = 0;
    for (int k = 0; k < LENGTH; k++) {
      Color c = b.get(k);
      if (c != Color.NONE) h ^= ZOB[k][c.ordinal()];
    }
    return h;
  }

  @Override
  public Move think(Board board) {
    // 相手の直前手を内部盤面へ反映
    this.board = this.board.placed(board.getMove());

    List<Move> legalMoves = this.board.findLegalMoves(getColor());
    if (legalMoves.size() == 1 && legalMoves.get(0).isPass()) {
      this.move = legalMoves.get(0);
    } else {
      // 黒視点で探索するため、白番なら盤面を反転
      MyBoard root = isBlack() ? this.board.clone() : this.board.flipped();
      this.move = null;
      this.nodes = 0;
      search(root, NEG_INF, POS_INF, 0);
      this.move = this.move.colored(getColor());
    }

    this.board = this.board.placed(this.move);
    return this.move;
  }

  /**
   * 黒視点固定の局面 root を、プレイヤーの depthLimit まで探索する。
   * 実験用エントリポイント（{@link #move} に最善手、戻り値に評価値）。
   */
  public double searchRoot(MyBoard root) {
    this.move = null;
    this.nodes = 0;
    return search(root, NEG_INF, POS_INF, 0);
  }

  /**
   * negamax + NegaScout 本体。手番側（= 反転により常に黒）から見た評価値を返す。
   */
  double search(MyBoard board, double alpha, double beta, int depth) {
    this.nodes++;

    if (board.isEnd() || depth > this.depthLimit) {
      return this.eval.value(board); // 黒視点 = 手番側視点
    }

    double alphaOrig = alpha;
    int remaining = this.depthLimit - depth;
    Move ttMove = null;

    // --- 置換表の参照 ---
    if (this.useTransposition) {
      long key = zobrist(board);
      TranspositionTable.Entry e = this.tt.probe(key);
      if (e != null) {
        ttMove = e.best;
        // ルート(depth==0)では this.move を必ず確定させたいので値枝刈りはしない
        if (depth > 0 && e.depth >= remaining) {
          if (e.flag == TranspositionTable.EXACT) return e.value;
          if (e.flag == TranspositionTable.LOWER) alpha = Math.max(alpha, e.value);
          else if (e.flag == TranspositionTable.UPPER) beta = Math.min(beta, e.value);
          if (alpha >= beta) return e.value;
        }
      }
    }

    List<Move> moves = ordered(board, ttMove);
    double best = NEG_INF;
    Move bestMove = moves.get(0);
    boolean first = true;

    for (Move m : moves) {
      MyBoard child = board.placed(m).flipped();
      double v;
      if (!this.useScout || first) {
        v = -search(child, -beta, -alpha, depth + 1);
      } else {
        // ヌル窓スカウト: α を超えるかだけを判定
        v = -search(child, -(alpha + EPS), -alpha, depth + 1);
        if (v > alpha && v < beta) {
          // fail-high したので通常窓で再探索
          v = -search(child, -beta, -alpha, depth + 1);
        }
      }

      if (v > best) {
        best = v;
        bestMove = m;
      }
      if (v > alpha) alpha = v;
      if (alpha >= beta) break; // β カット
      first = false;
    }

    if (depth == 0) this.move = bestMove;

    // --- 置換表へ保存 ---
    if (this.useTransposition) {
      int flag = best <= alphaOrig ? TranspositionTable.UPPER
               : best >= beta ? TranspositionTable.LOWER
               : TranspositionTable.EXACT;
      this.tt.store(zobrist(board), best, flag, remaining, bestMove);
    }

    return best;
  }

  /**
   * 黒の合法手を move ordering で並べて返す。
   * 置換表の最善手を先頭に、それ以外は 1 手読みの評価値で降順（黒に有利な順）。
   */
  List<Move> ordered(MyBoard board, Move ttMove) {
    List<Move> moves = board.findLegalMoves(BLACK);
    if (moves.size() <= 1) return moves;

    List<Move> sorted = new ArrayList<>(moves);
    // 値で降順、同値はマス番号昇順で決定的に
    Comparator<Move> byValue =
        Comparator.comparingDouble((Move m) -> -this.eval.value(board.placed(m)))
            .thenComparingInt(Move::getIndex);
    sorted.sort(byValue);

    if (ttMove != null && sorted.remove(ttMove)) {
      sorted.add(0, ttMove);
    }
    return sorted;
  }

  /**
   * 並列探索（{@link ParallelPlayer}）用。ルートの 1 手 m を着手した子局面を
   * フル窓で評価し、ルート手番から見た値を返す。
   */
  double evalRootMove(MyBoard root, Move m) {
    return evalRootMove(root, m, NEG_INF);
  }

  /**
   * 並列探索用。下界 α を指定してルート手 m を評価する。
   * β は +∞ 固定なので「m が α より良いか」を窓 (α, ∞) で調べることになり、
   * α より悪い手は早期に β カットで打ち切られる（ルート最善手の選択には十分）。
   */
  double evalRootMove(MyBoard root, Move m, double alpha) {
    MyBoard child = root.placed(m).flipped();
    return -search(child, NEG_INF, -alpha, 1);
  }
}
