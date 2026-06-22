package p26x29;

import java.util.List;
import java.util.Scanner;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;

/**
 * 課題 1b。人間が標準入力から着手を入力して対戦に参加するプレイヤー。
 *
 * <p>手番が来ると現在の盤面と合法手を表示し、{@code "c3"} のような座標
 * 文字列の入力を促す。合法手のみ受理し、不正な入力は再入力させる。
 * 合法手がパスのみの場合は自動的にパスする。
 */
public class HumanPlayer extends Player {

  /** 標準入力。ゲーム中は閉じないので static で 1 つだけ持つ。 */
  static final Scanner IN = new Scanner(System.in);

  public HumanPlayer(Color color) {
    super("H", color);
  }

  @Override
  public Move think(Board board) {
    List<Move> legals = board.findLegalMoves(getColor());

    // パスしかできない場合は自動でパス
    if (legals.size() == 1 && legals.get(0).isPass()) {
      System.out.println("[" + getColor() + "] 合法手なし。パスします。");
      return legals.get(0);
    }

    System.out.println(board);
    System.out.println("[" + getColor() + "] あなたの手番です。着手位置を入力 (例: c3"
        + (containsPass(legals) ? " / pass=.." : "") + "):");
    System.out.println("  合法手: " + Move.toStringList(legalIndexes(legals)));

    while (true) {
      String input = IN.next().trim();
      Move move = parse(input);
      if (move != null && legals.contains(move)) {
        return move;
      }
      System.out.println("  不正な手です。合法手から選んで再入力してください: ");
    }
  }

  /** 入力文字列を Move に変換。".." はパス。形式不正なら null。 */
  Move parse(String input) {
    try {
      if (input.equals("..") || input.equalsIgnoreCase("pass")) {
        return Move.ofPass(getColor());
      }
      if (input.length() != 2) return null;
      char col = input.charAt(0);
      char row = input.charAt(1);
      if (col < 'a' || col >= 'a' + Board.SIZE) return null;
      if (row < '1' || row >= '1' + Board.SIZE) return null;
      return Move.of(input, getColor());
    } catch (RuntimeException e) {
      return null;
    }
  }

  boolean containsPass(List<Move> legals) {
    return legals.stream().anyMatch(Move::isPass);
  }

  /** 合法手の盤面座標リスト（表示用）。 */
  List<Integer> legalIndexes(List<Move> legals) {
    return legals.stream().map(Move::getIndex).toList();
  }
}
