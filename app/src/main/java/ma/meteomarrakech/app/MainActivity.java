package ma.meteomarrakech.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 200;
    private SharedPreferences preferences;
    private TextView volumeLabel;
    private SeekBar volumeBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("meteo_settings", MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Météo Marrakech");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("Marrakech Ménara\n\nAnnonces vocales en français chaque heure.\nAlertes pluie et vents forts à partir de 40 km/h.");
        description.setTextSize(17);
        description.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 32, 0, 32);
        layout.addView(description, params);

        TextView volumeTitle = new TextView(this);
        volumeTitle.setText("Volume vocal de l'application");
        volumeTitle.setTextSize(20);
        volumeTitle.setGravity(Gravity.CENTER);
        layout.addView(volumeTitle);

        volumeLabel = new TextView(this);
        volumeLabel.setTextSize(18);
        volumeLabel.setGravity(Gravity.CENTER);
        layout.addView(volumeLabel);

        volumeBar = new SeekBar(this);
        volumeBar.setMax(100);
        volumeBar.setProgress(preferences.getInt("voice_volume", 70));
        updateVolumeLabel(volumeBar.getProgress());

        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeLabel(progress);
                if (fromUser) {
                    preferences.edit().putInt("voice_volume", progress).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(volumeBar);

        LinearLayout volumeButtons = new LinearLayout(this);
        volumeButtons.setOrientation(LinearLayout.HORIZONTAL);
        volumeButtons.setGravity(Gravity.CENTER);

        Button decrease = new Button(this);
        decrease.setText("− خفض");
        decrease.setOnClickListener(v ->
            volumeBar.setProgress(Math.max(0, volumeBar.getProgress() - 5))
        );
        volumeButtons.addView(decrease);

        Button increase = new Button(this);
        increase.setText("+ زيادة");
        increase.setOnClickListener(v ->
            volumeBar.setProgress(Math.min(100, volumeBar.getProgress() + 5))
        );
        volumeButtons.addView(increase);

        layout.addView(volumeButtons);

        Button start = new Button(this);
        start.setText("Démarrer le service météo");
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS
                );
            } else {
                startWeatherService();
            }
        });
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("Arrêter le service météo");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, WeatherService.class));
            Toast.makeText(this, "Service arrêté", Toast.LENGTH_SHORT).show();
        });
        layout.addView(stop);

        setContentView(layout);
    }

    private void updateVolumeLabel(int value) {
        volumeLabel.setText("Volume : " + value + "%");
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            startWeatherService();
        }
    }

    private void startWeatherService() {
        Intent intent = new Intent(this, WeatherService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Service météo démarré", Toast.LENGTH_SHORT).show();
    }
}
