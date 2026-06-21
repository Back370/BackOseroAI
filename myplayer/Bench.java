package myplayer;

import static ap26.Color.BLACK;
import static ap26.Color.WHITE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;

/**
 * 課題 1 の実験を自動化するための簡易ベンチマーク。
 *
 * <p>{@link MyGame} は 1 局ごとに盤面を標準出力へ大量に描画するため、
 * 数百局規模の勝率測定には不向きである。本クラスは盤面描画を行わない
 * 静かなゲームループで多数の対局を回し、勝敗だけを集計する。
 *
 * <p>本クラスは実験専用のドライバであり、AI 本体の強さには影響しない。
 */
public class Bench {

  /** 1 局を最後まで進め、黒石数 − 白石数（黒視点スコア）を返す。 */
  static int playOne(Player black, Player white, Board init) {
    Board board = init.clone();
    black.setBoard(board.clone());
    white.setBoard(board.clone());

    while (board.isEnd() == false) {
      Color turn = board.getTurn();
      Player p = (turn == BLACK) ? black : white;

      Move move;
      try {
        move = p.think(board.clone()).colored(turn);
      } catch (Error e) {
        board.foul(turn);
        return board.score();
      }

      List<Move> legals = board.findLegalMoves(turn);
      if (move == null || legals.contains(move) == false) {
        board.foul(turn);
        return board.score();
      }
      board = board.placed(move);
    }
    return board.score();
  }

  /** 勝率測定の結果（注目プレイヤー視点）。 */
  static class Result {
    int win, lose, draw, games;
    double winRate() { return 100.0 * win / games; }
    // 引き分けを 0.5 勝として数えた得点率
    double scoreRate() { return 100.0 * (win + 0.5 * draw) / games; }
    public String toString() {
      return String.format("%3d勝 %3d敗 %3d分 / %3d局  勝率 %5.1f%%  得点率 %5.1f%%",
          win, lose, draw, games, winRate(), scoreRate());
    }
  }

  /**
   * 注目プレイヤー hero と対戦相手 foe を games 局対戦させる。
   * 先手・後手を半々で入れ替える。result は hero 視点で集計。
   */
  static Result match(Function<Color, Player> hero, Function<Color, Player> foe,
      int games) {
    Result r = new Result();
    r.games = games;
    Board init = new MyBoard();
    for (int i = 0; i < games; i++) {
      boolean heroIsBlack = (i % 2 == 0);
      Player black = heroIsBlack ? hero.apply(BLACK) : foe.apply(BLACK);
      Player white = heroIsBlack ? foe.apply(WHITE) : hero.apply(WHITE);

      int score = playOne(black, white, init); // 黒視点
      int heroScore = heroIsBlack ? score : -score;

      if (heroScore > 0) r.win++;
      else if (heroScore < 0) r.lose++;
      else r.draw++;
    }
    return r;
  }

  public static void main(String[] args) {
    String exp = args.length > 0 ? args[0] : "all";

    if (exp.equals("depth") || exp.equals("all")) {
      System.out.println("## 実験1: 探索深さ depthLimit と勝率 (vs RandomPlayer, 各50局)");
      for (int d = 0; d <= 4; d++) {
        final int depth = d;
        Result r = match(c -> new MyPlayer("MY", c, depth),
            c -> new RandomPlayer(c), 50);
        System.out.printf("depthLimit=%d : %s%n", d, r);
      }
      System.out.println();
    }

    if (exp.equals("shuffle") || exp.equals("all")) {
      System.out.println("## 実験2: order()のshuffle有無 (depth=2, vs RandomPlayer, 各50局)");
      Result on = match(c -> new MyPlayer("MY", c, 2), c -> new RandomPlayer(c), 50);
      Result off = match(c -> new NoShufflePlayer("NS", c, 2), c -> new RandomPlayer(c), 50);
      System.out.printf("shuffle 有 : %s%n", on);
      System.out.printf("shuffle 無 : %s%n", off);
      System.out.println();
    }

    if (exp.equals("c") || exp.equals("all")) {
      System.out.println("## 課題1c: MyPlayer vs RandomPlayer 200局 (先手後手各100)");
      Result r = match(c -> new MyPlayer("MY", c, 2), c -> new RandomPlayer(c), 200);
      System.out.printf("MyPlayer(depth=2) : %s%n", r);
      System.out.println();
    }

    if (exp.equals("d") || exp.equals("all")) {
      System.out.println("## 課題1d: 重み行列Mの改良 (depth=2)");
      Function<Color, Player> base = c -> new MyPlayer("BASE", c, 2);
      Function<Color, Player> impr =
          c -> new MyPlayer("IMP", c, new ImprovedEval(), 2);

      Result baseVsRand = match(base, c -> new RandomPlayer(c), 100);
      Result imprVsRand = match(impr, c -> new RandomPlayer(c), 100);
      Result imprVsBase = match(impr, base, 100);
      System.out.printf("(1)改良前 vs Random : %s%n", baseVsRand);
      System.out.printf("(3)改良後 vs Random : %s%n", imprVsRand);
      System.out.printf("(4)改良後 vs 改良前  : %s%n", imprVsBase);
      System.out.println();
    }

    if (exp.equals("e") || exp.equals("all")) {
      System.out.println("## 課題1e: 評価関数phi(マス重み+機動力+石数) (depth=2)");
      Function<Color, Player> impr =
          c -> new MyPlayer("IMP", c, new ImprovedEval(), 2);
      Function<Color, Player> phi =
          c -> new MyPlayer("PHI", c, new PhiEval(), 2);

      Result phiVsRand = match(phi, c -> new RandomPlayer(c), 100);
      Result phiVsImpr = match(phi, impr, 100);
      System.out.printf("phi評価 vs Random   : %s%n", phiVsRand);
      System.out.printf("phi評価 vs 改良マス重み: %s%n", phiVsImpr);
      System.out.println();
    }

    if (exp.equals("f") || exp.equals("all")) {
      System.out.println("## 課題1f: move ordering の枝刈り効果 (depth=4, 各40局, 評価呼出回数)");
      // CountingEval で葉評価回数を数え、ランダム順 vs phi整列順 を比較
      CountingEval.reset();
      Function<Color, Player> rnd =
          c -> new MyPlayer("RND", c, new CountingEval(new ImprovedEval()), 4);
      Result a = match(rnd, c -> new RandomPlayer(c), 40);
      long randCalls = CountingEval.calls;

      CountingEval.reset();
      Function<Color, Player> ord =
          c -> new OrderingPlayer("ORD", c, new CountingEval(new ImprovedEval()), 4);
      Result b = match(ord, c -> new RandomPlayer(c), 40);
      long ordCalls = CountingEval.calls;

      System.out.printf("ランダム順   : 評価呼出 %,d 回, 勝率 %5.1f%%%n", randCalls, a.winRate());
      System.out.printf("move ordering: 評価呼出 %,d 回, 勝率 %5.1f%%%n", ordCalls, b.winRate());
      System.out.printf("評価呼出の削減率 : %.1f%%%n", 100.0 * (randCalls - ordCalls) / randCalls);
      System.out.println();
    }

    if (exp.equals("g") || exp.equals("all")) {
      benchNegaScout();
    }
    if (exp.equals("h") || exp.equals("all")) {
      benchParallel();
    }
    if (exp.equals("i") || exp.equals("all")) {
      benchBitBoard();
    }
  }

  // ===================== 課題1g: 置換表付き move ordering → NegaScout =====================

  /**
   * 初期局面を各深さで探索し、純 α-β（move ordering のみ）→ 置換表追加 →
   * NegaScout 追加 と段階的に枝刈りを強めたときの訪問ノード数を比較する。
   * 三者は<b>同じ最善手・同じ評価値</b>を返す（探索結果は不変、コストだけ減る）ことを示す。
   */
  static void benchNegaScout() {
    System.out.println("## 課題1g: 置換表付き move ordering / NegaScout の枝刈り効果");
    System.out.println("（標準初期局面・黒視点固定を各深さで1回探索, ImprovedEval）");
    System.out.printf("%5s | %-22s | %14s | %6s | %12s%n",
        "depth", "手法", "訪問ノード数", "最善手", "評価値");
    System.out.println("------+------------------------+----------------+--------+-------------");
    MyEval eval = new ImprovedEval();
    for (int d = 4; d <= 8; d++) {
      // (1) 純α-β: 置換表なし・NegaScoutなし（move orderingのみ）
      NegaScoutPlayer ab =
          new NegaScoutPlayer("AB", BLACK, eval, d, false, false);
      double vab = ab.searchRoot(new MyBoard());
      long nab = ab.nodes;
      // (2) 置換表付き move ordering（NegaScoutなし）
      NegaScoutPlayer tt =
          new NegaScoutPlayer("TT", BLACK, eval, d, true, false);
      double vtt = tt.searchRoot(new MyBoard());
      long ntt = tt.nodes;
      // (3) 置換表付き NegaScout
      NegaScoutPlayer ns =
          new NegaScoutPlayer("NS", BLACK, eval, d, true, true);
      double vns = ns.searchRoot(new MyBoard());
      long nns = ns.nodes;

      System.out.printf("%5d | %-22s | %,14d | %6s | %12.1f%n",
          d, "α-β (orderingのみ)", nab, ab.move, vab);
      System.out.printf("%5s | %-22s | %,14d | %6s | %12.1f  (%.1f%%減)%n",
          "", "+ 置換表", ntt, tt.move, vtt, 100.0 * (nab - ntt) / nab);
      System.out.printf("%5s | %-22s | %,14d | %6s | %12.1f  (%.1f%%減)%n",
          "", "+ NegaScout", nns, ns.move, vns, 100.0 * (nab - nns) / nab);
      boolean same = vab == vtt && vtt == vns
          && ab.move.equals(tt.move) && tt.move.equals(ns.move);
      System.out.printf("%5s | 結果一致: %s%n", "", same ? "○ (最善手・評価値とも同一)" : "× 不一致!");
    }
    System.out.println();
  }

  // ===================== 課題1h: マルチスレッド並列探索 =====================

  /**
   * 初期局面を固定深さで探索し、逐次（1スレッド）NegaScout と
   * ルート分割マルチスレッド版の実時間・ノード数・速度向上比を比較する。
   */
  static void benchParallel() {
    System.out.println("## 課題1h: マルチスレッド並列探索（ルート分割）の速度向上");
    int depth = 9;
    int cores = Runtime.getRuntime().availableProcessors();
    System.out.printf("（標準初期局面・depth=%d・ImprovedEval, 論理コア数=%d）%n", depth, cores);
    MyEval eval = new ImprovedEval();

    // JIT ウォームアップ
    new NegaScoutPlayer("warm", BLACK, eval, depth - 2, true, true)
        .searchRoot(new MyBoard());

    // 逐次（1スレッド）NegaScout
    NegaScoutPlayer seq = new NegaScoutPlayer("SEQ", BLACK, eval, depth);
    long t0 = System.nanoTime();
    seq.searchRoot(new MyBoard());
    double seqMs = (System.nanoTime() - t0) / 1e6;
    long seqNodes = seq.nodes;
    System.out.printf("%-26s : %8.1f ms | %,12d nodes | 速度向上 %4.2fx (基準)%n",
        "逐次 1スレッド", seqMs, seqNodes, 1.0);

    System.out.println("-- (A) 素のルート分割（各手フル窓・α非共有） --");
    for (int threads : new int[] {2, 4, 8}) {
      ParallelPlayer par = new ParallelPlayer("PAR", BLACK, eval, depth, threads, false);
      long t1 = System.nanoTime();
      par.searchRootParallel(new MyBoard());
      double parMs = (System.nanoTime() - t1) / 1e6;
      System.out.printf("%-26s : %8.1f ms | %,12d nodes | 速度向上 %4.2fx%n",
          "  " + threads + "スレッド", parMs, par.nodes, seqMs / parMs);
    }
    System.out.println("-- (B) α共有（YBWC: PV先読み後に並列） --");
    for (int threads : new int[] {2, 4, 8}) {
      ParallelPlayer par = new ParallelPlayer("PAR", BLACK, eval, depth, threads, true);
      long t1 = System.nanoTime();
      par.searchRootParallel(new MyBoard());
      double parMs = (System.nanoTime() - t1) / 1e6;
      System.out.printf("%-26s : %8.1f ms | %,12d nodes | 速度向上 %4.2fx%n",
          "  " + threads + "スレッド", parMs, par.nodes, seqMs / parMs);
    }
    System.out.println();
  }

  // ===================== 課題1i: ビットボードによる合法手検出 =====================

  /**
   * 配列走査版 {@link MyBoard#findLegalMoves} とビットボード版
   * {@link BitBoard#legalMoves} の (1) 結果一致 (2) スループットを比較する。
   * テスト局面はランダム対局で集める（標準盤）。
   */
  static void benchBitBoard() {
    System.out.println("## 課題1i: ビットボードによる着手可能位置検出の高速化");

    // ランダム自己対戦で多様な局面を収集
    List<MyBoard> positions = new ArrayList<>();
    java.util.Random rnd = new java.util.Random(20260622L);
    for (int g = 0; g < 400; g++) {
      MyBoard b = new MyBoard();
      while (!b.isEnd()) {
        positions.add(b);
        Color turn = b.getTurn();
        List<Move> ms = b.findLegalMoves(turn);
        b = b.placed(ms.get(rnd.nextInt(ms.size())));
      }
    }
    System.out.printf("テスト局面数: %,d（標準盤・ランダム対局から収集）%n", positions.size());

    // (1) 正当性: 全局面・両色で着手可能マス集合が完全一致するか
    int mismatches = 0;
    for (MyBoard b : positions) {
      for (Color c : new Color[] {BLACK, WHITE}) {
        List<Integer> arr = b.findLegalMoves(c).stream()
            .map(Move::getIndex).filter(k -> k >= 0).sorted().toList();
        List<Integer> bit = BitBoard.toIndexList(BitBoard.from(b).legalMoves(c));
        if (!arr.equals(bit)) mismatches++;
      }
    }
    System.out.printf("正当性検査: %s（不一致 %d 件 / %,d 局面×2色）%n",
        mismatches == 0 ? "○ 完全一致" : "× 不一致あり", mismatches, positions.size());

    // (2) スループット: 各局面×両色の合法手検出を繰り返し計測
    int reps = 200;
    // 配列走査版（ウォームアップ込み）
    long sink = 0;
    for (int w = 0; w < 50; w++)
      for (MyBoard b : positions) sink += b.findLegalMoves(BLACK).size();
    long t0 = System.nanoTime();
    for (int r = 0; r < reps; r++)
      for (MyBoard b : positions) {
        sink += b.findLegalMoves(BLACK).size();
        sink += b.findLegalMoves(WHITE).size();
      }
    double arrMs = (System.nanoTime() - t0) / 1e6;

    // ビットボード版（Board からの構築コストも含める / 構築済みも別途計測）
    BitBoard[] bbs = positions.stream().map(BitBoard::from).toArray(BitBoard[]::new);
    for (int w = 0; w < 50; w++)
      for (BitBoard bb : bbs) sink += Long.bitCount(bb.legalMoves(BLACK));
    long t1 = System.nanoTime();
    for (int r = 0; r < reps; r++)
      for (BitBoard bb : bbs) {
        sink += Long.bitCount(bb.legalMoves(BLACK));
        sink += Long.bitCount(bb.legalMoves(WHITE));
      }
    double bitMs = (System.nanoTime() - t1) / 1e6;

    long calls = (long) reps * positions.size() * 2;
    System.out.printf("配列走査版    : %8.1f ms / %,d回 = %6.3f Mcalls/s%n",
        arrMs, calls, calls / arrMs / 1000.0);
    System.out.printf("ビットボード版: %8.1f ms / %,d回 = %6.3f Mcalls/s  (%.2fx 高速)%n",
        bitMs, calls, calls / bitMs / 1000.0, arrMs / bitMs);
    if (sink == Long.MIN_VALUE) System.out.println(sink); // 最適化除去防止
    System.out.println();
  }
}
