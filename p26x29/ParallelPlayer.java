package p26x29;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import ap26.Board;
import ap26.Color;
import ap26.Move;

/**
 * 課題 1h 用。{@link NegaScoutPlayer} の探索をマルチスレッドで並列化した
 * プレイヤー。
 *
 * <h2>並列化の方式: ルート分割（root splitting）</h2>
 * ルート局面の合法手を {@code threads} 個のスレッドへ<b>重複なく</b>分配し、
 * 各スレッドが「自分に割り当てられたルート手の部分木だけ」を独立に
 * NegaScout で読む。最後に各スレッドの最良値を集約して全体の最善手を選ぶ。
 *
 * <p>課題が要求する「各スレッドで探索する局面がスレッド間で重複しない
 * ための工夫」は、<b>ルートの手をスレッドに排他的に割り当てる</b>ことで
 * 満たす（異なるルート手から始まる部分木はほぼ素である）。さらに各スレッドに
 * <b>専用の置換表</b>を持たせることで、共有データへの書き込み競合を完全に
 * 排除し、ロックなしでスケールさせる。
 *
 * <h2>α の共有（Young Brothers Wait Concept）</h2>
 * 単純なルート分割は各手をフル窓で読むため総ノード数が大きく増える。
 * そこで本実装は
 * <ol>
 *   <li>move ordering 先頭（最有力 = PV）の手だけ<b>先に逐次</b>で読み、
 *       暫定最善値 α を確定させる</li>
 *   <li>残りの手を並列に読む。各タスクは共有 α（{@link java.util.concurrent.atomic.AtomicLong}
 *       で保持）を下界として窓 (α, ∞) で探索し、α より悪い手を早期に
 *       β カットする。良い手が見つかれば α を {@code compareAndSet} で更新する</li>
 * </ol>
 * とすることで、フル窓ルート分割よりノード増加を抑えつつ並列化する。
 *
 * <h2>トレードオフ</h2>
 * それでも α 更新はタスク完了時にしか伝播しないため、逐次 α-β ほど
 * 鋭くは枝刈りできず<b>総ノード数は逐次版よりやや増える</b>。また 6×6 の
 * ルート合法手は最大でも十数手なので、並列度はルートの分岐数で頭打ちになる。
 */
public class ParallelPlayer extends NegaScoutPlayer {

  final int threads;
  /** 共有 α（YBWC）を使うか。false なら各手をフル窓で読む素のルート分割。 */
  final boolean shareAlpha;
  /** 並び替え用の補助ワーカー（ルート手の決定的整列に使う）。 */
  final NegaScoutPlayer orderer;

  public ParallelPlayer(String name, Color color, MyEval eval, int depthLimit,
      int threads) {
    this(name, color, eval, depthLimit, threads, true);
  }

  public ParallelPlayer(String name, Color color, MyEval eval, int depthLimit,
      int threads, boolean shareAlpha) {
    super(name, color, eval, depthLimit, true, true);
    this.threads = threads;
    this.shareAlpha = shareAlpha;
    this.orderer = new NegaScoutPlayer(name + "#ord", color, eval, depthLimit);
  }

  @Override
  public Move think(Board board) {
    this.board = this.board.placed(board.getMove());

    List<Move> legalMoves = this.board.findLegalMoves(getColor());
    if (legalMoves.size() == 1 && legalMoves.get(0).isPass()) {
      this.move = legalMoves.get(0);
    } else {
      MyBoard root = isBlack() ? this.board.clone() : this.board.flipped();
      Move best = searchRootParallel(root);
      this.move = best.colored(getColor());
    }

    this.board = this.board.placed(this.move);
    return this.move;
  }

  /** ルート局面（黒手番固定）を並列探索し、黒の最善手を返す。 */
  public Move searchRootParallel(MyBoard root) {
    // ルート手は決定的な move ordering 順で取り出す
    List<Move> moves = orderer.ordered(root, null);
    if (moves.size() == 1) {
      this.nodes = 0;
      return moves.get(0);
    }

    // スレッド数だけ専用ワーカー（専用置換表）を用意し、ThreadLocal で各
    // 実スレッドに固定する。プールの同時実行は threads 個までなので、
    // 同じワーカーを 2 スレッドが同時に使うことはない（競合フリー）。
    ThreadLocal<NegaScoutPlayer> local = ThreadLocal.withInitial(
        () -> new NegaScoutPlayer("worker", getColor(),
            this.eval, this.depthLimit));
    AtomicLong totalNodes = new AtomicLong();

    double best = NEG_INF;
    Move bestMove = moves.get(0);
    int firstParallel = 0;
    // 共有 α（double を long ビット列で保持）
    AtomicLong alphaBits = new AtomicLong(Double.doubleToLongBits(NEG_INF));

    if (shareAlpha) {
      // (1) PV（最有力手）を先に逐次で読み、下界 α を確定させる
      orderer.nodes = 0;
      best = orderer.evalRootMove(root, moves.get(0));
      totalNodes.addAndGet(orderer.nodes);
      alphaBits.set(Double.doubleToLongBits(best));
      firstParallel = 1; // PV は済んだので 1 番目から並列
    }

    // (2) 残りの手を並列に読む
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<Object[]>> futures = new ArrayList<>();
    for (int i = firstParallel; i < moves.size(); i++) {
      final Move m = moves.get(i);
      Callable<Object[]> task = () -> {
        NegaScoutPlayer w = local.get();
        w.nodes = 0;
        double v;
        if (shareAlpha) {
          double alpha = Double.longBitsToDouble(alphaBits.get());
          v = w.evalRootMove(root, m, alpha);  // 窓 (α, ∞)
          if (v > alpha) updateMax(alphaBits, v);
        } else {
          v = w.evalRootMove(root, m);          // フル窓 (-∞, ∞)
        }
        totalNodes.addAndGet(w.nodes);
        return new Object[] {v, m};
      };
      futures.add(pool.submit(task));
    }

    try {
      for (Future<Object[]> f : futures) {
        Object[] r = f.get();
        double v = (double) r[0];
        Move m = (Move) r[1];
        if (v > best) {
          best = v;
          bestMove = m;
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    } finally {
      pool.shutdown();
    }

    this.nodes = totalNodes.get(); // 全スレッド合計の訪問ノード数
    this.move = bestMove;
    return bestMove;
  }

  /** AtomicLong に詰めた double を、value がより大きい場合だけ更新する。 */
  static void updateMax(AtomicLong bits, double value) {
    long next = Double.doubleToLongBits(value);
    while (true) {
      long cur = bits.get();
      if (Double.longBitsToDouble(cur) >= value) return;
      if (bits.compareAndSet(cur, next)) return;
    }
  }
}
