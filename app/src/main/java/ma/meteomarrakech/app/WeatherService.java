package ma.meteomarrakech.app;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.ToneGenerator;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

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
    private ToneGenerator toneGenerator;
    private Handler handler;

    private volatile boolean ttsReady = false;
    private int lastHour = -1;
    private int lastDay = -1;

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());
        createChannel();

        Notification notification = new Notification.Builder(this, CHANNEL)
            .setContentTitle("Météo Marrakech")
            .setContentText("Service météo actif — annonces en français")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();

        startForeground(4217, notification);

        PowerManager pm =
            (PowerManager) getSystemService(POWER_SERVICE);

        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MeteoMarrakech:Weather"
        );
        wakeLock.acquire();

        toneGenerator = new ToneGenerator(
            android.media.AudioManager.STREAM_NOTIFICATION, 80
        );

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.FRANCE);

                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;

                if (ttsReady) {
                    AudioAttributes audioAttributes =
                        new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SPEECH
                            )
                            .build();

                    tts.setAudioAttributes(audioAttributes);

                    tts.setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {

                            @Override
                            public void onStart(String id) {
                            }

                            @Override
                            public void onDone(String id) {
                                handler.post(() -> playBeep());
                            }

                            @Override
                            public void onError(String id) {
                            }
                        }
                    );
                }
            }
        });

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            this::checkTime, 0, 30, TimeUnit.SECONDS
        );
    }

    private void checkTime() {
        Calendar cal = Calendar.getInstance(
            TimeZone.getTimeZone("Africa/Casablanca")
        );

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int day = cal.get(Calendar.DAY_OF_YEAR);

        if (minute == 0 && (hour != lastHour || day != lastDay)) {
            lastHour = hour;
            lastDay = day;
            fetchWeather(hour);
        }
    }

    private String hourText(int hour) {
        return hour == 1 ? "une heure" : hour + " heures";
    }

    private void fetchWeather(int hour) {
        try {
            URL url = new URL(
                "https://api.open-meteo.com/v1/forecast"
                + "?latitude=31.6069&longitude=-8.0363"
                + "&current=temperature_2m,precipitation,rain,"
                + "wind_speed_10m,wind_gusts_10m"
                + "&hourly=precipitation_probability"
                + "&forecast_days=2&timezone=Africa%2FCasablanca"
            );

            HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            StringBuilder body = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }

            } finally {
                connection.disconnect();
            }

            JSONObject root = new JSONObject(body.toString());
            JSONObject current = root.getJSONObject("current");

            int temp = (int) Math.round(
                current.getDouble("temperature_2m")
            );

            double rain = current.optDouble("rain", 0);
            double precipitation =
                current.optDouble("precipitation", 0);

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

            int maxProbability = -1;
            int rainHour = -1;

            for (int i = 0;
                 i < times.length() && i < probabilities.length();
                 i++) {

                String time = times.optString(i, "");

                if (time.length() < 13 ||
                    time.substring(0, 13).compareTo(currentTime) < 0) {
                    continue;
                }

                int probability = probabilities.isNull(i)
                    ? 0
                    : probabilities.optInt(i, 0);

                if (maxProbability < probability) {
                    maxProbability = probability;
                    rainHour = Integer.parseInt(time.substring(11, 13));
                }
            }

            StringBuilder message = new StringBuilder();

            message.append("Il est ")
                .append(hourText(hour))
                .append(". ");

            message.append("La température actuelle est de ")
                .append(temp)
                .append(" degrés Celsius. ");

            if (rain > 0 || precipitation > 0) {
                message.append(
                    "Des précipitations sont en cours actuellement. "
                );
            }

            if (maxProbability >= 20 && rainHour >= 0) {
                message.append(
                    "La probabilité de pluie atteint "
                )
                .append(maxProbability)
                .append(" pour cent vers ")
                .append(hourText(rainHour))
                .append(". ");

            } else {
                message.append(
                    "Aucune pluie significative n'est prévue "
                    + "dans les douze prochaines heures. "
                );
            }

            double strongest = Math.max(wind, gust);

            if (strongest >= 40) {
                message.append(
                    "Attention, des vents forts sont prévus. "
                    + "Le vent peut atteindre "
                )
                .append(Math.round(strongest))
                .append(" kilomètres par heure. ");
            }

            speak(message.toString());

        } catch (Exception e) {
            speak(
                "Impossible de récupérer les données météo. "
                + "Vérifiez la connexion Internet."
            );
        }
    }

    private void playBeep() {
        if (toneGenerator != null) {
            toneGenerator.startTone(
                ToneGenerator.TONE_PROP_BEEP2, 180
            );
        }
    }

    private void speak(String message) {
        if (!ttsReady || tts == null) {
            return;
        }

        int volume = getSharedPreferences(
            "meteo_settings", MODE_PRIVATE
        ).getInt("voice_volume", 70);

        float level = Math.max(0, Math.min(100, volume)) / 100f;

        Bundle params = new Bundle();
        params.putFloat(
            TextToSpeech.Engine.KEY_PARAM_VOLUME, level
        );

        String utteranceId = "meteo_" + System.currentTimeMillis();

        handler.post(() -> {
            playBeep();

            handler.postDelayed(() -> {
                if (tts != null && ttsReady) {
                    tts.speak(
                        message,
                        TextToSpeech.QUEUE_FLUSH,
                        params,
                        utteranceId
                    );
                }
            }, 300);
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                "Service météo",
                NotificationManager.IMPORTANCE_LOW
            );

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
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        if (toneGenerator != null) {
            toneGenerator.release();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
