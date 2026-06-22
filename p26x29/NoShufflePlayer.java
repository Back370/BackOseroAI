package p26x29;

import java.util.List;

import ap26.Color;
import ap26.Move;

/**
 * 実験2用。{@link MyPlayer#order(List)} のシャッフルを無効化した版。
 * 同じ評価値の手が複数あるとき、常に同じ（リスト先頭側の）手を選ぶ。
 */
public class NoShufflePlayer extends MyPlayer {

  public NoShufflePlayer(String name, Color color, int depthLimit) {
    super(name, color, depthLimit);
  }

  /** シャッフルせず、生成された手順のまま返す。 */
  @Override
  List<Move> order(List<Move> moves) {
    return moves;
  }
}
