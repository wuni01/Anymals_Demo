package com.example.anymals_demo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;

public class UploadPinActivity extends AppCompatActivity {

    private ImageButton uploadPhotoBtn;
    private ImageView selectedImageView;
    private EditText etComment;
    private Uri imageUri = null; // 선택된 이미지 경로
    private double lat, lng;

    // 갤러리 실행을 위한 Launcher
    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    selectedImageView.setImageURI(imageUri);
                    selectedImageView.setVisibility(View.VISIBLE); // 이미지 보여주기
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_pin);

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

        lat = getIntent().getDoubleExtra("lat", 0.0);
        lng = getIntent().getDoubleExtra("lng", 0.0);

        uploadPhotoBtn = findViewById(R.id.Upload_Photo_btn);
        selectedImageView = findViewById(R.id.selectedImageView);
        etComment = findViewById(R.id.etComment);
        AppCompatButton registerBtn = findViewById(R.id.Register_Pin_btn);

        uploadPhotoBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        registerBtn.setOnClickListener(v -> {
            Log.d("CheckClick", "버튼이 눌렸습니다!");
            uploadPin();
        });
    }

    private void uploadPin() {
        String comment = etComment.getText().toString().trim();
        if (comment.isEmpty()) {
            Toast.makeText(this, "코멘트를 입력해주세요!", Toast.LENGTH_SHORT).show();
            return;
        }

        String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference sharedRef = FirebaseDatabase.getInstance().getReference("SharedPins");
        String pinId = sharedRef.push().getKey();

        if (imageUri != null) {
            StorageReference fileRef = FirebaseStorage.getInstance().getReference("PinImages").child(pinId + ".jpg");
            fileRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            saveToDatabase(pinId, myUid, comment, uri.toString());
                        });
            }).addOnFailureListener(e -> {
                        // 여기서 에러가 나면 토스트가 뜹니다.
                        Log.e("FirebaseError", "Storage 업로드 실패", e);
                        Toast.makeText(this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
        else {
            saveToDatabase(pinId, myUid, comment, "");
        }
    }

    private void saveToDatabase(String pinId, String uid, String comment, String imageUrl) {
        long currentTimestamp = System.currentTimeMillis();

        PinModel pin = new PinModel(pinId, uid, comment, lat, lng, imageUrl, currentTimestamp);

        DatabaseReference sharedPinsRef = FirebaseDatabase.getInstance().getReference("SharedPins");
        sharedPinsRef.child(pinId).setValue(pin)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "핀이 공유되었습니다!", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
}