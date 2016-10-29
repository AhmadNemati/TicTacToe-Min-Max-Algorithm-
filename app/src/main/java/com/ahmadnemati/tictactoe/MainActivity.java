package com.ahmadnemati.tictactoe;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.view.Menu;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends RxAppCompatActivity {

    private static final String EXTRA_GRID_STATE = "gridState";
    private static final String EXTRA_IS_GAME_OVER = "isGameOver";
    private static final String EXTRA_CURRENT_PLAYER = "currentPlayer";
    private static final String EXTRA_GAME_STATE = "gameState";
    private static final String EXTRA_WINNING_INDICES = "winningIndices";
    private static final String PREF_HUMAN_WINS = "humanWins";
    private static final String PREF_COMPUTER_WINS = "computer_wins";
    private static final String PREF_TIES = "ties";

    @Bind(R.id.ttt_view)
    protected TicTacToeView ticTacToeView;

    private final TicTacToeGame game = new TicTacToeGame();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        ticTacToeView.setOnTileClickedListener(new TicTacToeView.OnTileClickListener() {
            @Override
            public void onTileClick(int position) {
                handleMove(position);
            }
        });

        game.setOnGameOverListener(new TicTacToeGame.OnGameOverListener() {
            @Override
            public void onGameOver(@TicTacToeGame.GameState int state, int[] winningIndices) {
                endGame(state, winningIndices);
            }
        });

        if (savedInstanceState != null) {
            ticTacToeView.setEnabled(false);
            game.setGridState(savedInstanceState.getCharArray(EXTRA_GRID_STATE));
            game.setIsOver(savedInstanceState.getBoolean(EXTRA_IS_GAME_OVER));
            game.setCurrentPlayer(savedInstanceState.getChar(EXTRA_CURRENT_PLAYER));
            game.setGameState(savedInstanceState.getInt(EXTRA_GAME_STATE));
            ticTacToeView.restoreBoard(game.getGridState());

            if (game.isOver()) {
                game.setWinningIndices(savedInstanceState.getIntArray(EXTRA_WINNING_INDICES));
                game.endGame();
            } else {
                ticTacToeView.setNextPlayer(game.currentPlayer());
            }
        } else {
            ticTacToeView.setNextPlayer(game.currentPlayer());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!game.isOver()) {
            startGame();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharArray(EXTRA_GRID_STATE, game.getGridState());
        outState.putBoolean(EXTRA_IS_GAME_OVER, game.isOver());
        outState.putChar(EXTRA_CURRENT_PLAYER, game.currentPlayer());
        outState.putInt(EXTRA_GAME_STATE, game.getGameState());
        outState.putIntArray(EXTRA_WINNING_INDICES, game.getWinningIndices());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.restart:
                restart();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startGame() {
        ticTacToeView.setNextPlayer(game.currentPlayer());
        if (game.currentPlayer() == TicTacToeGame.PLAYER_TWO) {
            simulateCpuMove();
        } else {
            ticTacToeView.setEnabled(true);
            Snackbar.make(ticTacToeView, "Your turn!", Snackbar.LENGTH_LONG).show();
        }
    }

    private void handleMove(int position) {
        game.makeMove(position);
        char nextPlayer = game.currentPlayer();
        ticTacToeView.setNextPlayer(nextPlayer);

        if (!game.isOver()) {
            if (nextPlayer == TicTacToeGame.PLAYER_TWO) {
                simulateCpuMove();
            }
        }
    }


    private void simulateCpuMove() {
        final Snackbar snackbar = Snackbar.make(ticTacToeView, "Thinking...", Snackbar.LENGTH_INDEFINITE);
        game.getCpuMove()
                .subscribeOn(Schedulers.computation())
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        ticTacToeView.setEnabled(false);
                        snackbar.show();
                    }
                })
                .delay(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        ticTacToeView.setEnabled(true);
                        ticTacToeView.setTile(game.getNextCpuMove(), TicTacToeGame.PLAYER_TWO);
                        handleMove(game.getNextCpuMove());
                        snackbar.dismiss();
                    }
                });
    }

    private void endGame(@TicTacToeGame.GameState int result, @Nullable int[] winningIndices) {
        ticTacToeView.endGame(winningIndices);
        if (getSupportFragmentManager().findFragmentByTag("scores") == null) {
            showScoresDialog(result);
        }
    }

    private void restart() {
        ticTacToeView.reset();
        game.restart();
        startGame();
    }

    @SuppressLint("CommitPrefEdits")
    private void showScoresDialog(@TicTacToeGame.GameState final int result) {
        final boolean isDone = result != TicTacToeGame.CONTINUE;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String title;
        String scoreToUpdate = null;
        switch (result) {
            case TicTacToeGame.ONE_WINS:
                title = "You win!";
                scoreToUpdate = PREF_HUMAN_WINS;
                break;
            case TicTacToeGame.TWO_WINS:
                title = "Computer wins!";
                scoreToUpdate = PREF_COMPUTER_WINS;
                break;
            case TicTacToeGame.TIE:
                title = "It's a tie!";
                scoreToUpdate = PREF_TIES;
                break;
            default:
                title = "History";
        }

        if (scoreToUpdate != null) {
            prefs.edit().putInt(scoreToUpdate, prefs.getInt(scoreToUpdate, 0) + 1).commit();
        }

        String scores = prefs.getInt(PREF_HUMAN_WINS, 0) + " - Human"
                + "\n" + prefs.getInt(PREF_COMPUTER_WINS, 0) + " - Computer"
                + "\n" + prefs.getInt(PREF_TIES, 0) + " - Ties";

        ScoresDialogFragment dialog = new ScoresDialogFragment();
        dialog.isDone = isDone;
        dialog.title = title;
        dialog.message = scores;
        dialog.prefs = prefs;

        dialog.show(getSupportFragmentManager(), "scores");
    }


    public static class ScoresDialogFragment extends DialogFragment {

        boolean isDone;
        String message;
        String title;
        SharedPreferences prefs;

        public ScoresDialogFragment() {
            setRetainInstance(true);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            MaterialDialog.ButtonCallback buttonCallback = new MaterialDialog.ButtonCallback() {
                @Override
                public void onPositive(MaterialDialog dialog) {
                    if (isDone) {
                        ((MainActivity) getActivity()).restart();
                    }
                }

                @Override
                public void onNegative(MaterialDialog dialog) {
                    prefs.edit()
                            .putInt(PREF_HUMAN_WINS, 0)
                            .putInt(PREF_COMPUTER_WINS, 0)
                            .putInt(PREF_TIES, 0)
                            .apply();
                }
            };

            MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .title(title)
                    .content(message)
                    .positiveText(isDone ? "Restart" : "Done")
                    .callback(buttonCallback);

            if (!isDone) {
                builder.negativeText("Clear scores");
            }

            return builder.build();
        }

        @Override
        public void onDestroyView() {

            if (getDialog() != null && getRetainInstance()) {
                getDialog().setDismissMessage(null);
            }
            super.onDestroyView();
        }
    }
}
