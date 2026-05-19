package com.example.anymals_demo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UploadPinActivity extends AppCompatActivity {

    private ImageButton uploadPhotoBtn;
    private ImageView selectedImageView;
    private EditText etComment;
    private Uri imageUri = null;
    private Uri cameraPhotoUri = null;
    private double lat, lng;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
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

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (cameraPhotoUri != null) {
                        startCrop(cameraPhotoUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        imageUri = resultUri;
                        selectedImageView.setImageURI(imageUri);
                        selectedImageView.setVisibility(View.VISIBLE);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR) {
                    Throwable cropError = UCrop.getError(result.getData());
                    Log.e("CropError", cropError.getMessage());
                    Toast.makeText(this, "이미지 편집 실패: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_pin);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            final WindowInsetsController controller = getWindow().getInsetsController();

            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        lat = getIntent().getDoubleExtra("lat", 0.0);
        lng = getIntent().getDoubleExtra("lng", 0.0);

        uploadPhotoBtn = findViewById(R.id.Upload_Photo_btn);
        selectedImageView = findViewById(R.id.selectedImageView);
        etComment = findViewById(R.id.etComment);
        AppCompatButton registerBtn = findViewById(R.id.Register_Pin_btn);

        uploadPhotoBtn.setOnClickListener(v -> showImageChooseDialog());

        registerBtn.setOnClickListener(v -> {
            Log.d("CheckClick", "버튼이 눌렸습니다!");
            uploadPin();
        });
    }

    private void showImageChooseDialog() {
        String[] options = {"갤러리에서 선택", "카메라로 촬영"};
        new AlertDialog.Builder(this)
                .setTitle("핀 사진 설정")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        pickImageLauncher.launch(intent);
                    } else if (which == 1) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 201);
                        } else {
                            openCamera();
                        }
                    }
                })
                .show();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File photoFile = createUniqueImageFile();
            if (photoFile != null) {
                cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cameraLauncher.launch(takePictureIntent);
            }
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "실행 가능한 카메라 앱이 없습니다.", Toast.LENGTH_SHORT).show();
        } catch (IOException ex) {
            Toast.makeText(this, "카메라 준비 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private File createUniqueImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "PIN_ORIGINAL_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void startCrop(@NonNull Uri sourceUri) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String destinationFileName = "CROPPED_PIN_" + timeStamp + ".jpg";

        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(90);
        options.setToolbarTitle("사진 편집");

        options.setFreeStyleCropEnabled(true);

        UCrop uCrop = UCrop.of(sourceUri, Uri.fromFile(new File(getExternalCacheDir(), destinationFileName)))
                .withOptions(options);

        Intent uCropIntent = uCrop.getIntent(this);
        cropLauncher.launch(uCropIntent);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 201) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}