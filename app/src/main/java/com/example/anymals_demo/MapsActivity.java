package com.example.anymals_demo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PathOverlay;
import java.util.ArrayList;
import java.util.List;
import com.naver.maps.map.LocationTrackingMode;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private long start_walk;
    private double lat, lng;
    private View loadingLayout;
    private Button finish_walk_button;
    private LatLng lastValidLatLng = null;
    private long lastUpdateTime = 0;
    private PathOverlay path = new PathOverlay();
    private List<LatLng> coords = new ArrayList<>();
    private NaverMap naverMap;
    private com.naver.maps.map.util.FusedLocationSource locationSource;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private DatabaseReference pinsRef;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private ImageView ivPinDetail;
    private TextView tvPinComment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        loadingLayout = findViewById(R.id.loading_layout);

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

        View bottomSheet = findViewById(R.id.pin_bottom_sheet);
        ivPinDetail = findViewById(R.id.iv_pin_detail);
        tvPinComment = findViewById(R.id.tv_pin_comment);

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetBehavior.setSaveFlags(BottomSheetBehavior.SAVE_ALL);
        bottomSheetBehavior.setFitToContents(true);
        bottomSheetBehavior.setSkipCollapsed(true);
//        bottomSheetBehavior.setFitToContents(false);
//        bottomSheetBehavior.setHalfExpandedRatio(0.5f);

        lat = getIntent().getDoubleExtra("lat", 0.0);
        lng = getIntent().getDoubleExtra("lng", 0.0);

        MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().add(R.id.map, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        start_walk = System.currentTimeMillis();

        finish_walk_button = findViewById(R.id.finish_walk_btn);
        finish_walk_button.setOnClickListener(v -> showWalkSummary());
    }

    private double calculateTotalDistance() {
        double totalDistance = 0;
        for (int i = 0; i < coords.size() - 1; i++) {
            totalDistance += coords.get(i).distanceTo(coords.get(i + 1));
        }
        return totalDistance;
    }

    private void showWalkSummary() {
        long endTime = System.currentTimeMillis();
        long walkTimeMillis = endTime - start_walk;
        double distance = calculateTotalDistance();
        String distanceStr = (distance >= 1000) ?
                String.format("%.2f km", distance / 1000) :
                (int)distance + " m";

        int minutes = (int) (walkTimeMillis / (1000 * 60));
        int seconds = (int) (walkTimeMillis / 1000) % 60;

        String summary = "🐾 산책 완료!\n\n" +
                "총 산책 시간: " + minutes + "분 " + seconds + "초\n" +
                "총 이동 거리: " + distanceStr + "\n" +
                "고생하셨어요!";

        new AlertDialog.Builder(this)
                .setTitle("산책 정산")
                .setMessage(summary)
                .setPositiveButton("확인", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        pinsRef = FirebaseDatabase.getInstance().getReference("SharedPins");

        locationSource = new com.naver.maps.map.util.FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        naverMap.getLocationOverlay().setVisible(true);

        naverMap.getUiSettings().setCompassEnabled(false);
        naverMap.getUiSettings().setZoomControlEnabled(false);
        com.naver.maps.map.widget.CompassView compassView = findViewById(R.id.compass_view);
        compassView.setMap(naverMap);
        com.naver.maps.map.widget.LocationButtonView locationButtonView = findViewById(R.id.location_btn);
        locationButtonView.setMap(naverMap);

        naverMap.moveCamera(CameraUpdate.zoomTo(18.5));

        path.setColor(Color.parseColor("#4CAF50"));
        path.setOutlineColor(Color.WHITE);
        path.setWidth(20);

        naverMap.setOnMapClickListener((point, coord) -> {
            if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        naverMap.addOnLocationChangeListener(location -> {
            if (loadingLayout.getVisibility() == View.VISIBLE) {
                loadingLayout.animate()
                        .alpha(0.0f)
                        .setDuration(500)
                        .withEndAction(() -> loadingLayout.setVisibility(View.GONE));
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime < 3000) {
                return;
            }

            if (location.getAccuracy() > 35) {
                return;
            }

            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            if (lastValidLatLng != null) {
                double distance = lastValidLatLng.distanceTo(currentLatLng);

                if (distance < 4.5) {
                    return;
                }
            }

            lastValidLatLng = currentLatLng;
            lastUpdateTime = currentTime;
            naverMap.getLocationOverlay().setPosition(currentLatLng);
            naverMap.moveCamera(CameraUpdate.scrollTo(currentLatLng));

            if (coords.isEmpty() || !coords.get(coords.size() - 1).equals(currentLatLng)) {
                coords.add(currentLatLng);
            }
            if (coords.size() >= 2) {
                path.setCoords(coords);
                path.setMap(naverMap);
            }
        });

        findViewById(R.id.btn_add_comment).setOnClickListener(v -> {
            android.location.Location lastLocation = locationSource.getLastLocation();
            if (lastLocation != null) {
                Intent intent = new Intent(this, UploadPinActivity.class);
                intent.putExtra("lat", lastLocation.getLatitude());
                intent.putExtra("lng", lastLocation.getLongitude());
                startActivity(intent);
            }
        });

        loadSharedPins();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                if (naverMap != null) {
                    naverMap.setLocationTrackingMode(LocationTrackingMode.None);
                }
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void loadSharedPins() {
        pinsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // [팁] 새로운 데이터를 받기 전에 기존 마커들을 지우고 싶다면
                // naverMap.clear()를 쓰거나 마커 리스트를 관리하세요.

                for (DataSnapshot data : snapshot.getChildren()) {
                    PinModel pin = data.getValue(PinModel.class);
                    if (pin != null) {
                        createMarkerWithPhoto(pin);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showPinDetail(PinModel pin) {
        tvPinComment.setText(pin.comment);

        if (pin.imageUrl != null && !pin.imageUrl.isEmpty()) {
            ivPinDetail.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this)
                    .load(pin.imageUrl)
                    .into(ivPinDetail);
        } else {
            ivPinDetail.setVisibility(View.GONE);
        }

        TextView tvUploaderName = findViewById(R.id.txt_username);
        TextView tvUploadDate = findViewById(R.id.txt_timestamp);
        ImageView ivUploaderProfile = findViewById(R.id.img_user_profile);

        if (pin.timestamp > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy년 MM월 dd일", java.util.Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));

            tvUploadDate.setVisibility(View.VISIBLE); // 다시 보이게 설정
            tvUploadDate.setText(sdf.format(new java.util.Date(pin.timestamp)));
        }
        else {
            tvUploadDate.setText("작성일 없음");
            tvUploadDate.setVisibility(View.GONE);
        }

        if (pin.uid != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users")
                    .child(pin.uid).child("petProfile");

            userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Pet pet = snapshot.getValue(Pet.class);
                        if (pet != null) {
                            tvUploaderName.setText(pet.name);

                            if (pet.profileImageUrl != null && !pet.profileImageUrl.isEmpty()) {
                                com.bumptech.glide.Glide.with(MapsActivity.this)
                                        .load(pet.profileImageUrl)
                                        .into(ivUploaderProfile);
                            } else {
                                ivUploaderProfile.setImageResource(R.drawable.ic_pet_default);
                            }
                        }
                    } else {
                        tvUploaderName.setText("익명의 Anymal");
                        ivUploaderProfile.setImageResource(R.drawable.ic_pet_default);
                    }
                }

                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    android.util.Log.e("FirebaseError", "유저 정보 로드 실패: " + error.getMessage());
                }
            });
        } else {
            tvUploaderName.setText("익명");
            ivUploaderProfile.setImageResource(R.drawable.ic_pet_default);
        }

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void createMarkerWithPhoto(PinModel pin) {
        View view = LayoutInflater.from(this).inflate(R.layout.marker, null);
        ImageView markerImage = view.findViewById(R.id.marker_image);

        if (pin.imageUrl != null && !pin.imageUrl.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(pin.imageUrl)
                    .override(120, 120)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            markerImage.setImageBitmap(resource);

                            Bitmap finalBitmap = createTransparentMarkerBitmap(view);
                            addMarker(pin, finalBitmap);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        } else {
            markerImage.setImageResource(R.drawable.ic_pet_default);

            Bitmap finalBitmap = createTransparentMarkerBitmap(view);
            addMarker(pin, finalBitmap);
        }
    }

    private Bitmap createTransparentMarkerBitmap(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);

        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        view.draw(canvas);

        return bitmap;
    }

    private void addMarker(PinModel pin, Bitmap bitmap) {
        Marker marker = new Marker();
        marker.setPosition(new LatLng(pin.lat, pin.lng));

        marker.setIcon(OverlayImage.fromBitmap(bitmap));
        marker.setAnchor(new PointF(0.5f, 1.0f));
        marker.setMap(naverMap);

        marker.setOnClickListener(overlay -> {
            showPinDetail(pin);
            return true;
        });
    }
}