package com.example.anymals_demo;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.time.LocalDate;

public class RegisterPetActivity extends AppCompatActivity {

    private EditText register_pet_name, register_pet_breed, register_pet_birthday, register_pet_weight;
    private RadioGroup register_pet_gender;
    private Button register_pet;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_pet);

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

        register_pet_name = findViewById(R.id.register_pet_name);
        register_pet_breed = findViewById(R.id.register_pet_breed);
        register_pet_birthday = findViewById(R.id.register_pet_birthday);
        register_pet_weight = findViewById(R.id.register_pet_weight);
        register_pet_gender = findViewById(R.id.register_pet_gender);
        register_pet = findViewById(R.id.register_pet);

        register_pet_birthday.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting || s.length() == 0) return;
                isFormatting = true;

                String clean = s.toString().replaceAll("[^\\d]", "");
                if (clean.length() > 8) {
                    clean = clean.substring(0, 8);
                }

                int length = clean.length();
                StringBuilder formatted = new StringBuilder();

                if (length <= 4) {
                    formatted.append(clean);
                } else if (length <= 6) {
                    formatted.append(clean.substring(0, 4)).append("-").append(clean.substring(4));
                } else {
                    formatted.append(clean.substring(0, 4))
                            .append("-")
                            .append(clean.substring(4, 6))
                            .append("-")
                            .append(clean.substring(6));
                }

                register_pet_birthday.removeTextChangedListener(this);
                register_pet_birthday.setText(formatted.toString());
                register_pet_birthday.setSelection(formatted.length());
                register_pet_birthday.addTextChangedListener(this);

                isFormatting = false;
            }
        });

        String dbUrl = "https://anymals-a6441-default-rtdb.firebaseio.com/";
        mDatabase = FirebaseDatabase.getInstance(dbUrl).getReference();

        register_pet.setOnClickListener(v -> savePetInfo());
    }

    private void savePetInfo() {
        String name = register_pet_name.getText().toString().trim();
        String breed = register_pet_breed.getText().toString().trim();
        String weight = register_pet_weight.getText().toString().trim();
        String bd = register_pet_birthday.getText().toString().trim();
        String gender = "NULL";

        int check_gender = register_pet_gender.getCheckedRadioButtonId();
        if(check_gender == R.id.pet_male) {
            gender = "수컷";
        }
        else if(check_gender == R.id.pet_female) {
            gender = "암컷";
        }

        if(name.isEmpty() || breed.isEmpty() || bd.isEmpty() || weight.isEmpty() || "NULL".equals(gender)) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        String birthday = Pet.get_Birthday(bd);
        String age = Pet.get_Age(bd);

        try {
            Pet pet = new Pet(name, gender, breed, birthday, Double.parseDouble(weight), age);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if(user != null) {
                String uid = user.getUid();

                mDatabase.child("Users").child(uid).child("petProfile").setValue(pet)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "등록이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, "등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
            else {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "몸무게 형식이 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}