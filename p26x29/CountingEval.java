package myplayer;

import ap26.Board;

/**
 * 課題 1f 用。別の評価関数 delegate をラップし、{@code value()} の呼び出し
 * 回数（= 葉ノードの評価回数）を静的カウンタで数える。move ordering による
 * 枝刈り効果を「評価呼び出し回数の削減」で測るために用いる。
 */
public class CountingEval extends MyEval {
  static long calls = 0;
  final MyEval delegate;

  public CountingEval(MyEval delegate) {
    this.delegate = delegate;
  }

  static void reset() { calls = 0; }

  @Override
  public float value(Board board) {
    calls++;
    return this.delegate.value(board);
  }
}
