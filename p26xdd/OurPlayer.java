package p26x29;

import static ap26.Color.BLACK;
import static ap26.Color.WHITE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;
import myplayer.MyEval;

public class OurPlayer extends Player {
  private static final String PLAYER_NAME = "XD26";
  private static final int DEFAULT_DEPTH_LIMIT = 2;

  private final MyEval eval;
  private final int depthLimit;
  private MyBoard board;
  private Move move;

  public OurPlayer(Color color) {
    this(color, new MyEval(), DEFAULT_DEPTH_LIMIT);
  }

  OurPlayer(Color color, MyEval eval, int depthLimit) {
    super(PLAYER_NAME, color);
    this.eval = eval;
    this.depthLimit = depthLimit;
    this.board = new MyBoard();
  }

  @Override
  public void setBoard(Board board) {
    syncBoard(board);
  }

  @Override
  public Move think(Board board) {
    syncBoard(board);

    List<Move> legalMoves = this.board.findLegalMoves(getColor());
    if (hasNoPlayableMove(legalMoves)) {
      this.move = Move.ofPass(getColor());
    } else {
      MyBoard searchBoard = isBlack() ? this.board.clone() : this.board.flipped();
      this.move = null;
      maxSearch(searchBoard, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0);
      this.move = this.move.colored(getColor());
    }

    this.board = this.board.placed(this.move);
    return this.move;
  }

  private void syncBoard(Board board) {
    super.setBoard(board);
    if (this.board == null) {
      this.board = new MyBoard(board);
      return;
    }
    this.board.syncFrom(board);
  }

  private boolean isBlack() {
    return getColor() == BLACK;
  }

  private boolean hasNoPlayableMove(List<Move> legalMoves) {
    return legalMoves.isEmpty() || legalMoves.stream().allMatch(Move::isPass);
  }

  private float maxSearch(Board currentBoard, float alpha, float beta, int depth) {
    if (isTerminal(currentBoard, depth)) {
      return this.eval.value(currentBoard);
    }

    List<Move> moves = order(currentBoard.findLegalMoves(BLACK));
    if (moves.isEmpty()) {
      return this.eval.value(currentBoard);
    }

    if (depth == 0) {
      this.move = moves.get(0);
    }

    for (Move nextMove : moves) {
      Board nextBoard = currentBoard.placed(nextMove);
      float childValue = minSearch(nextBoard, alpha, beta, depth + 1);

      if (childValue > alpha) {
        alpha = childValue;
        if (depth == 0) {
          this.move = nextMove;
        }
      }

      if (alpha >= beta) {
        break;
      }
    }

    return alpha;
  }

  private float minSearch(Board currentBoard, float alpha, float beta, int depth) {
    if (isTerminal(currentBoard, depth)) {
      return this.eval.value(currentBoard);
    }

    List<Move> moves = order(currentBoard.findLegalMoves(WHITE));
    if (moves.isEmpty()) {
      return this.eval.value(currentBoard);
    }

    for (Move nextMove : moves) {
      Board nextBoard = currentBoard.placed(nextMove);
      float childValue = maxSearch(nextBoard, alpha, beta, depth + 1);
      beta = Math.min(beta, childValue);

      if (alpha >= beta) {
        break;
      }
    }

    return beta;
  }

  private boolean isTerminal(Board currentBoard, int depth) {
    return currentBoard.isEnd() || depth > this.depthLimit;
  }

  private List<Move> order(List<Move> moves) {
    List<Move> shuffled = new ArrayList<>(moves);
    Collections.shuffle(shuffled);
    return shuffled;
  }
}
