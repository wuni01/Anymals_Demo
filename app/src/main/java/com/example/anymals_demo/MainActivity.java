package com.example.anymals_demo;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends AppCompatActivity {
    private TextView tv_temperature;
    private TextView tv_weather_desc;
    private TextView tv_walk_recommend;
    private ImageView iv_weather_icon;
    private TextView tv_humidity;
    private TextView tvDailyGoal;
    private TextView tvDistanceValue;
    private TextView tvCaloriesValue;
    private TextView tvTimeValue;
    private ProgressBar progressWalkRatio;
    private TextView tvSectionAction;


    private double dailyGoalKm = 5.0;
    private static final String KEY_REH = "last_reh";
    private com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient;
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private static final String PREFS_NAME = "WeatherPrefs";
    private static final String KEY_TEMP = "last_temp";
    private static final String KEY_PTY = "last_pty";
    private ImageView pet_profile_image;
    private Uri photoUri;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> cropLauncher;
    private String currentUid;
    private DatabaseReference statsRef;
    private ValueEventListener statsListener;
    private android.content.BroadcastReceiver dateChangeReceiver;

    private void saveWeatherData(String temp, String pty, String reh) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (temp != null) editor.putString(KEY_TEMP, temp);
        if (pty != null) editor.putString(KEY_PTY, pty);
        if (reh != null) editor.putString(KEY_REH, reh);
        editor.apply();
    }
    private void updateWeatherStatus(String ptyValue) {
        String status = ".";
        int resId = R.drawable.ic_sun;

        switch (ptyValue) {
            case "1": case "4":
                status = "비";
                resId = R.drawable.ic_rain;
                tv_walk_recommend.setText("비가 와요. 실내 놀이를 추천해요!");
                break;
            case "2": case "3":
                status = "눈";
                resId = R.drawable.ic_snow;
                tv_walk_recommend.setText("눈이 와요! 발바닥을 조심하세요");
                break;
            default:
                status = "맑음";
                resId = R.drawable.ic_sun;
                tv_walk_recommend.setText("산책하기 딱 좋은 날씨예요!");
                break;
        }
        tv_weather_desc.setText(status);
        iv_weather_icon.setImageResource(resId);
    }

    private void getCurrentLocationAndWeather() {
        com.google.android.gms.location.LocationRequest locationRequest =
                com.google.android.gms.location.LocationRequest.create()
                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                        .setInterval(100)
                        .setFastestInterval(50);

        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                if (locationResult == null) return;

                for (android.location.Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();

                        Log.d("LocationCheck", "내 위치: " + currentLat + ", " + currentLng);

                        getWeatherData(currentLat, currentLng);
                        fusedLocationClient.removeLocationUpdates(this);
                        break;
                    }
                }
            }
        }, android.os.Looper.getMainLooper());
    }

    private String SERVICE_KEY = com.example.anymals_demo.BuildConfig.WEATHER_API_KEY;
    private void getWeatherData(double lat, double lng) {
        TransLocalPoint.LatXLng gridPoint = TransLocalPoint.convertGridGps(TransLocalPoint.TO_GRID, lat, lng);
        int nx = gridPoint.x;
        int ny = gridPoint.y;

        Calendar cal = Calendar.getInstance();
        int min = cal.get(Calendar.MINUTE);
        if (min < 40) {
            cal.add(Calendar.HOUR_OF_DAY, -1);
        }

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH", Locale.getDefault());
        String baseDate = sdfDate.format(cal.getTime());
        String baseTime = sdfTime.format(cal.getTime()) + "00";
        Log.d("WeatherCheck", "요청 파라미터 - baseDate: " + baseDate + ", baseTime: " + baseTime + ", nx: " + nx + ", ny: " + ny);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WeatherInterface weatherInterface = retrofit.create(WeatherInterface.class);
        Call<GetWeather> call = weatherInterface.getUltraSrtNcst(
                SERVICE_KEY, 1, 60, "JSON", baseDate, baseTime, nx, ny
        );

        call.enqueue(new Callback<GetWeather>() {
            @Override
            public void onResponse(Call<GetWeather> call, Response<GetWeather> response) {
                if (isDestroyed() || isFinishing()) return;

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().response != null &&
                            response.body().response.body != null &&
                            response.body().response.body.items != null) {

                        List<GetWeather.WeatherItem> items = response.body().response.body.items.item;

                        if (items != null) {
                            for (GetWeather.WeatherItem item : items) {
                                if (item.category.equals("T1H")) {
                                    String temp = item.obsrValue;

                                    if (temp.contains(".")) {
                                        temp = temp.substring(0, temp.indexOf("."));
                                    }
                                    temp = temp + "°";

                                    tv_temperature.setText(temp);
                                    saveWeatherData(temp, null,null);
                                }
                                if (item.category.equals("PTY")) {
                                    updateWeatherStatus(item.obsrValue);
                                    saveWeatherData(null, item.obsrValue,null);
                                }
                                if (item.category.equals("REH")) {
                                    String humidity = "습도 " + item.obsrValue + "%";
                                    if (tv_humidity != null) tv_humidity.setText(humidity);
                                    saveWeatherData(null, null, humidity);
                                }
                            }
                        }
                    } else {
                        Log.e("WeatherError", "기상청 응답 바디가 비어있음");
                    }
                } else {
                    Log.e("WeatherError", "응답 실패 코드: " + response.code() + " 에러메시지: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<GetWeather> call, Throwable t) {
                Log.e("WeatherError", "통신 실패: " + t.getMessage());
            }
        });
    }

    private void startWalkButton() {
        Button walk_button = findViewById(R.id.start_walk_btn);

        walk_button.setEnabled(false);
        walk_button.setText("위치 동기화중...");
        walk_button.setAlpha(0.5f);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            walk_button.setEnabled(true);
            walk_button.setText("Go Anywhere!");
            walk_button.setAlpha(1.0f);
        }, 4500);

        walk_button.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapsActivity.class);
            intent.putExtra("lat", currentLat);
            intent.putExtra("lng", currentLng);

            Log.d("NowLocationCheck", "내 위치: " + currentLat + ", " + currentLng);

            startActivity(intent);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            final android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        View pet_empty_state = findViewById(R.id.layoutPetEmpty);
        View pet_profile = findViewById(R.id.layoutPetProfile);
        Button register_pet = findViewById(R.id.register_pet_main);
        pet_profile_image = findViewById(R.id.pet_Image);
        tv_temperature = findViewById(R.id.tv_temperature);
        tv_weather_desc = findViewById(R.id.tv_weather_desc);
        tv_walk_recommend = findViewById(R.id.tv_walk_recommend);
        tv_humidity = findViewById(R.id.tv_humidity);
        iv_weather_icon = findViewById(R.id.iv_weather_icon);
        tvDailyGoal = findViewById(R.id.tv_daily_goal);
        tvDistanceValue = findViewById(R.id.tv_distance_value);
        tvCaloriesValue = findViewById(R.id.tv_calories_value);
        tvTimeValue = findViewById(R.id.tv_time_value);
        progressWalkRatio = findViewById(R.id.progress_walk_ratio);
        tvSectionAction = findViewById(R.id.tv_section_action);

        tvSectionAction.setOnClickListener(v -> showGoalSettingDialog());
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);

        initImageLaunchers();

        dateChangeReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
                    Log.d("DateChange", "날짜가 변경되었습니다. 데이터를 초기화합니다.");
                    loadTodayActivityData();
                }
            }
        };
        registerReceiver(dateChangeReceiver, new IntentFilter(Intent.ACTION_DATE_CHANGED));

        ImageButton logoutButton = findViewById(R.id.logout_btn);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("로그아웃")
                        .setMessage("로그아웃 하시겠습니까?")
                        .setPositiveButton("취소", (dialog, which) -> {
                            dialog.dismiss();
                        })

                        .setNegativeButton("확인", (dialog, which) -> {
                            FirebaseAuth.getInstance().signOut();

                            Toast.makeText(MainActivity.this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);

                            finish();
                        })
                        .show();
            });
        }

        loadSavedWeatherData();

        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
        else {
            getCurrentLocationAndWeather();
        }

        register_pet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RegisterPetActivity.class);
                startActivity(intent);
            }
        });

        if (pet_profile_image != null) {
            pet_profile_image.setOnClickListener(v -> showImageChooseDialog());
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUid = user.getUid();
            loadTodayActivityData();

            DatabaseReference petRef = FirebaseDatabase.getInstance().getReference("Users").child(currentUid).child("petProfile");

            petRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        pet_profile.setVisibility(View.VISIBLE);
                        pet_empty_state.setVisibility(View.GONE);

                        Pet pet = snapshot.getValue(Pet.class);
                        if (pet != null) {
                            ((TextView)findViewById(R.id.pet_Name)).setText(pet.name);
                            ImageView genderIcon = findViewById(R.id.gender_Icon);
                            if ("수컷".equals(pet.gender))
                                genderIcon.setImageResource(R.drawable.ic_male);
                            else if ("암컷".equals(pet.gender))
                                genderIcon.setImageResource(R.drawable.ic_female);

                            ((TextView)findViewById(R.id.pet_Age)).setText("(" + pet.age + ")");
                            ((TextView)findViewById(R.id.pet_Breed)).setText(pet.breed);
                            ((TextView)findViewById(R.id.pet_Birthday)).setText("생일: " + pet.birthday);

                            if (pet.profileImageUrl != null && !pet.profileImageUrl.isEmpty()) {
                                com.bumptech.glide.Glide.with(MainActivity.this)
                                        .load(pet.profileImageUrl)
                                        .centerCrop()
                                        .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(30))
                                        .into(pet_profile_image);
                            } else {
                                pet_profile_image.setImageResource(R.drawable.ic_camera);
                            }
                        }
                    }
                    else {
                        pet_profile.setVisibility(View.GONE);
                        pet_empty_state.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    pet_empty_state.setVisibility(View.VISIBLE);
                }
            });
        }

        startWalkButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dateChangeReceiver != null) {
            unregisterReceiver(dateChangeReceiver);
        }
        if (statsRef != null && statsListener != null) {
            statsRef.removeEventListener(statsListener);
        }
    }

    private void showGoalSettingDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        editText.setHint("예: 5.0");
        editText.setSingleLine(true);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        int sideMargin = (int) (24 * getResources().getDisplayMetrics().density);
        int topBottomMargin = (int) (12 * getResources().getDisplayMetrics().density);

        params.setMargins(sideMargin, topBottomMargin, sideMargin, topBottomMargin);
        editText.setLayoutParams(params);

        container.addView(editText);

        new AlertDialog.Builder(this)
                .setTitle("오늘의 목표 거리 설정")
                .setMessage("목표 산책 거리(km)를 입력하세요.")
                .setView(container)
                .setPositiveButton("저장", (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        try {
                            dailyGoalKm = Double.parseDouble(input);
                            tvDailyGoal.setText(String.format(Locale.getDefault(), "목표: %.1fKM", dailyGoalKm));

                            if (currentUid != null) {
                                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString(currentUid + "_user_daily_goal", String.valueOf(dailyGoalKm));
                                editor.apply();
                            }

                            double currentDistance = Double.parseDouble(tvDistanceValue.getText().toString());
                            updateWalkProgressBar(currentDistance);
                        } catch (NumberFormatException e) {
                            Toast.makeText(MainActivity.this, "올바른 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void updateActivityUI(double totalDistanceKm, double totalCalories, int totalMinutes) {
        tvDistanceValue.setText(String.format(Locale.getDefault(), "%.2f", totalDistanceKm));
        tvCaloriesValue.setText(String.format(Locale.getDefault(), "%.1f", totalCalories));

        TextView tvTimeUnit = findViewById(R.id.tv_time_unit);

        if (totalMinutes >= 60) {
            int hours = totalMinutes / 60;
            int mins = totalMinutes % 60;

            if (mins > 0) {
                tvTimeValue.setText(hours + "시간 " + mins);
                tvTimeUnit.setText("분");
            } else {
                tvTimeValue.setText(String.valueOf(hours));
                tvTimeUnit.setText("시간");
            }
        } else {
            tvTimeValue.setText(String.valueOf(totalMinutes));
            tvTimeUnit.setText("분");
        }

        updateWalkProgressBar(totalDistanceKm);
    }

    private void updateWalkProgressBar(double currentDistanceKm) {
        if (dailyGoalKm > 0) {
            int ratio = (int) ((currentDistanceKm / dailyGoalKm) * 100);
            if (ratio > 100) ratio = 100;
            progressWalkRatio.setProgress(ratio);
        } else {
            progressWalkRatio.setProgress(0);
        }
    }

    private void loadTodayActivityData() {
        if (currentUid == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        String todayDate = sdf.format(new Date());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        dailyGoalKm = Double.parseDouble(prefs.getString(currentUid + "_user_daily_goal", "5.0"));
        tvDailyGoal.setText(String.format(Locale.getDefault(), "목표: %.1fKM", dailyGoalKm));

        if (statsRef != null && statsListener != null) {
            statsRef.removeEventListener(statsListener);
        }

        statsRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(currentUid).child("dailyStats").child(todayDate);

        statsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double totalDistanceKm = 0.0;
                double totalCalories = 0.0;
                int totalMinutes = 0;

                if (snapshot.exists()) {
                    Object dObj = snapshot.child("totalDistanceKm").getValue();
                    Object cObj = snapshot.child("totalCalories").getValue();
                    Object mObj = snapshot.child("totalMinutes").getValue();

                    if (dObj instanceof Number) totalDistanceKm = ((Number) dObj).doubleValue();
                    if (cObj instanceof Number) totalCalories = ((Number) cObj).doubleValue();
                    if (mObj instanceof Number) totalMinutes = ((Number) mObj).intValue();
                }

                updateActivityUI(totalDistanceKm, totalCalories, totalMinutes);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", "오늘의 활동 로드 실패: " + error.getMessage());
            }
        };
        statsRef.addValueEventListener(statsListener);
    }

    private void initImageLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            startCrop(selectedImageUri);
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (photoUri != null) {
                            startCrop(photoUri);
                        }
                    }
                }
        );

        cropLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri resultUri = UCrop.getOutput(result.getData());
                        if (resultUri != null) {
                            uploadImageToFirebase(resultUri);
                        }
                    } else if (result.getResultCode() == UCrop.RESULT_ERROR) {
                        Throwable cropError = UCrop.getError(result.getData());
                        Log.e("CropError", cropError.getMessage());
                        Toast.makeText(this, "이미지 자르기 실패: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showImageChooseDialog() {
        String[] options = {"갤러리에서 선택", "카메라로 촬영"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("프로필 사진 설정");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                galleryLauncher.launch(intent);
            } else if (which == 1) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 101);
                } else {
                    openCamera();
                }
            }
        });
        builder.show();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        try {
            File photoFile = createUniqueImageFile();
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                cameraLauncher.launch(takePictureIntent);
            }
        } catch (android.content.ActivityNotFoundException e) {
            Log.e("CameraError", "카메라 앱을 실행할 수 없음: " + e.getMessage());
            Toast.makeText(this, "실행 가능한 카메라 앱이 없습니다.", Toast.LENGTH_SHORT).show();
        } catch (IOException ex) {
            Log.e("CameraError", "파일 생성 오류: " + ex.getMessage());
            Toast.makeText(this, "카메라 준비 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCrop(@NonNull Uri sourceUri) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String destinationFileName = "CROPPED_PROFILE_" + timeStamp + ".jpg";

        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(90);
        options.setShowCropGrid(false);
        options.setShowCropFrame(true);
        options.setToolbarTitle("프로필 자르기");

        UCrop uCrop = UCrop.of(sourceUri, Uri.fromFile(new File(getExternalCacheDir(), destinationFileName)))
                .withAspectRatio(1, 1)
                .withMaxResultSize(1000, 1000)
                .withOptions(options);

        Intent uCropIntent = uCrop.getIntent(this);
        cropLauncher.launch(uCropIntent);
    }

    private File createUniqueImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "PET_PROFILE_" + timeStamp;

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
    }

    private void uploadImageToFirebase(Uri fileUri) {
        if (currentUid == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("pet_profiles").child(currentUid + ".jpg");

        storageRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadUrl = uri.toString();

                    // Realtime Database에 URL 업데이트 저장
                    DatabaseReference petRef = FirebaseDatabase.getInstance().getReference("Users")
                            .child(currentUid).child("petProfile");

                    petRef.child("profileImageUrl").setValue(downloadUrl)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(MainActivity.this, "프로필 사진이 변경되었습니다.", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "DB 업데이트 실패", Toast.LENGTH_SHORT).show());
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("StorageError", e.getMessage());
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadSavedWeatherData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedTemp = prefs.getString(KEY_TEMP, "--°");
        String savedPty = prefs.getString(KEY_PTY, "0");
        String savedReh = prefs.getString(KEY_REH, "습도 --%");

        if (tv_temperature != null) tv_temperature.setText(savedTemp);
        if (tv_humidity != null) tv_humidity.setText(savedReh);
        updateWeatherStatus(savedPty);
    }
}