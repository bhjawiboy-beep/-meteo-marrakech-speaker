package ma.meteomarrakech.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 200;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
