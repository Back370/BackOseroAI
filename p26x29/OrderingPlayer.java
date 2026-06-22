package p26x29;

import static ap26.Color.BLACK;
import static ap26.Color.WHITE;

import java.util.Comparator;
import java.util.List;

import ap26.Board;
import ap26.Color;
import ap26.Move;

/**
 * 課題 1f 用。move ordering を実装したプレイヤー。
 *
 * <p>各ノードで子手を 1 手だけ進めた局面の評価値 phi(qi) で並び替え、
 * よい手から先に探索する。max 側（黒）は phi 降順、min 側（白）は phi 昇順。
 * これにより α-β のカットが早く起き、探索する局面数が減ることを狙う。
 *
 * <p>並び替えのための浅い評価コストと、カットによる削減はトレードオフの
 * 関係にある。本実装では「子局面の 1 手読み評価」という最も浅い基準を用い、
 * コストを抑えている。
 */
public class OrderingPlayer extends MyPlayer {

  public OrderingPlayer(String name, Color color, MyEval eval, int depthLimit) {
    super(name, color, eval, depthLimit);
  }

  /** phi(子局面) で降順（max 側）に並べて探索。 */
  @Override
  float maxSearch(Board currentBoard, float alpha, float beta, int depth) {
    if (isTerminal(currentBoard, depth)) {
      return this.eval.value(currentBoard);
    }
    List<Move> moves = ordered(currentBoard, BLACK, true);

    if (depth == 0) {
      this.move = moves.get(0);
    }
    for (Move nextMove : moves) {
      Board nextBoard = currentBoard.placed(nextMove);
      float childValue = minSearch(nextBoard, alpha, beta, depth + 1);
      if (childValue > alpha) {
        alpha = childValue;
        if (depth == 0) this.move = nextMove;
      }
      if (alpha >= beta) break;
    }
    return alpha;
  }

  /** phi(子局面) で昇順（min 側）に並べて探索。 */
  @Override
  float minSearch(Board currentBoard, float alpha, float beta, int depth) {
    if (isTerminal(currentBoard, depth)) {
      return this.eval.value(currentBoard);
    }
    List<Move> moves = ordered(currentBoard, WHITE, false);

    for (Move nextMove : moves) {
      Board nextBoard = currentBoard.placed(nextMove);
      float childValue = maxSearch(nextBoard, alpha, beta, depth + 1);
      beta = Math.min(beta, childValue);
      if (alpha >= beta) break;
    }
    return beta;
  }

  /**
   * color の合法手を、1 手進めた局面の評価値で並べ替えて返す。
   * descending=true なら降順（黒に有利な順）、false なら昇順。
   */
  List<Move> ordered(Board board, Color color, boolean descending) {
    List<Move> moves = board.findLegalMoves(color);
    if (moves.size() <= 1) return moves;
    Comparator<Move> byPhi =
        Comparator.comparingDouble(m -> this.eval.value(board.placed(m)));
    if (descending) byPhi = byPhi.reversed();
    return moves.stream().sorted(byPhi).toList();
  }
}
