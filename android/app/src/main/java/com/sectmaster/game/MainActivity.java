package com.sectmaster.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Single-activity native Android game. No WebView, JavaScript, or network connection is used. */
public final class MainActivity extends Activity implements GameView.Host {
    private GameView gameView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        GameState state = GameState.load(this);
        String offline = state.offlineSummary(System.currentTimeMillis());
        gameView = new GameView(this, state, this);
        setContentView(gameView);
        hideSystemBars();
        if (offline != null) gameView.showMessage(offline);
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideSystemBars();
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.getState().save(this);
        super.onPause();
    }

    @Override public void requestReset() {
        new AlertDialog.Builder(this)
            .setTitle("Reset all progress?")
            .setMessage("This permanently erases your sect, disciples, resources, and achievements.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset", (dialog, which) -> {
                GameState.clearSave(this);
                GameState fresh = new GameState();
                fresh.save(this);
                gameView.replaceState(fresh);
                gameView.showMessage("A new sect has been founded.");
            }).show();
    }
}
