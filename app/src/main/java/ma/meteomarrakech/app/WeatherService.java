package ma.meteomarrakech.app;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.TextToSpeech;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class WeatherService extends Service {
    private static final String CHANNEL = "meteo_marrakech";
    private ScheduledExecutorService scheduler;
    private TextToSpeech tts;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean ttsReady = false;
    private int lastHour = -1;
    private int lastDay = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification notification = new Notification.Builder(this, CHANNEL)
            .setContentTitle("Météo Marrakech")
            .setContentText("Service météo actif — annonces en français")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();

        startForeground(4217, notification);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "MeteoMarrakech:Weather");
        wakeLock.acquire();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.FRANCE);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;

                if (ttsReady) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                    tts.setAudioAttributes(audioAttributes);
                    speak("Le service météo de Marrakech Ménara est activé.");
                }
            }
        });

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkTime, 0, 30, TimeUnit.SECONDS);
    }

    private void checkTime() {
        Calendar cal = Calendar.getInstance(
            TimeZone.getTimeZone("Africa/Casablanca"));
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int day = cal.get(Calendar.DAY_OF_YEAR);

        if (minute == 0 && (hour != lastHour || day != lastDay)) {
            lastHour = hour;
            lastDay = day;
            fetchWeather(hour);
        }
    }

    private void fetchWeather(int hour) {
        try {
            URL url = new URL(
                "https://api.open-meteo.com/v1/forecast"
                + "?latitude=31.6069&longitude=-8.0363"
                + "&current=temperature_2m,precipitation,rain,"
                + "wind_speed_10m,wind_gusts_10m"
                + "&hourly=precipitation_probability"
                + "&forecast_days=2&timezone=Africa%2FCasablanca");

            HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            } finally {
                connection.disconnect();
            }

            JSONObject root = new JSONObject(body.toString());
            JSONObject current = root.getJSONObject("current");
            int temp = (int) Math.round(
                current.getDouble("temperature_2m"));

            String hourText = hour == 1 ? "une heure" : hour + " heures";
            speak("Il est " + hourText
                + ". La température à Marrakech Ménara est de "
                + temp + " degrés Celsius.");

            double rain = current.optDouble("rain", 0);
            double precipitation = current.optDouble("precipitation", 0);
            double wind = current.optDouble("wind_speed_10m", 0);
            double gust = current.optDouble("wind_gusts_10m", 0);

            JSONObject hourly = root.getJSONObject("hourly");
            JSONArray times = hourly.getJSONArray("time");
            JSONArray probabilities =
                hourly.getJSONArray("precipitation_probability");

            String currentTime = current.optString("time", "");
            if (currentTime.length() >= 13) {
                currentTime = currentTime.substring(0, 13);
            }

            int probability = 0;
            for (int i = 0; i < times.length()
                    && i < probabilities.length(); i++) {
                String time = times.optString(i, "");
                if (time.length() >= 13
                        && time.substring(0, 13).equals(currentTime)) {
                    probability = probabilities.isNull(i)
                        ? 0 : probabilities.optInt(i, 0);
                    break;
                }
            }

            if (rain > 0 || precipitation > 0) {
                speak("Attention, des précipitations sont en cours.");
            } else if (probability >= 50) {
                speak("Attention, un risque de pluie est prévu "
                    + "dans les prochaines heures.");
            }

            double strongest = Math.max(wind, gust);
            if (strongest >= 40) {
                speak("Attention, vents forts à Marrakech Ménara. "
                    + "Le vent peut atteindre "
                    + Math.round(strongest)
                    + " kilomètres par heure.");
            }

        } catch (Exception e) {
            speak("Impossible de récupérer les données météo. "
                + "Vérifiez la connexion Internet.");
        }
    }

    private void speak(String message) {
        if (ttsReady && tts != null) {
            int volume = getSharedPreferences(
                "meteo_settings", MODE_PRIVATE
            ).getInt("voice_volume", 70);

            float level = Math.max(0, Math.min(100, volume)) / 100f;

            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, level);

            tts.speak(message, TextToSpeech.QUEUE_ADD, params, null);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Service météo", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
