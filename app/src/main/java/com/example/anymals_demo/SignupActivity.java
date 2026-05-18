package com.example.anymals_demo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class SignupActivity extends AppCompatActivity {

    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private boolean isEmailChecked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

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

        EditText register_email = findViewById(R.id.register_email);
        Button check_duplicate = findViewById(R.id.check_duplicate_btn);
        EditText register_password = findViewById(R.id.register_password);
        EditText register_password_confirm = findViewById(R.id.register_password_confirm);
        Button register_user = findViewById(R.id.register_user_btn);

        check_duplicate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = register_email.getText().toString().trim();

                if(email.isEmpty()) {
                    Toast.makeText(SignupActivity.this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                checkEmailDuplicate(email);
            }
        });

        register_user.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String password = register_password.getText().toString();
                String password_confirm = register_password_confirm.getText().toString();

                if (!isEmailChecked) {
                    Toast.makeText(SignupActivity.this, "이메일 중복 확인을 해주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (password.isEmpty()) {
                    Toast.makeText(SignupActivity.this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!password.equals(password_confirm)) {
                    Toast.makeText(SignupActivity.this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (password.length() < 6) {
                    Toast.makeText(SignupActivity.this, "비밀번호는 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseAuth mAuth = FirebaseAuth.getInstance();
                String email = register_email.getText().toString().trim();

                mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(SignupActivity.this, task -> {
                    if(task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        if(user != null) {
                            String uid = user.getUid();
                            saveUserToDatabase(uid, email);

                            Toast.makeText(SignupActivity.this, "회원 가입 성공!", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                    else {
                        Log.e("SignupError", "가입 실패 원인: ", task.getException());
                        Toast.makeText(SignupActivity.this, "회원 가입 실패: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void checkEmailDuplicate(String email) {

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");
        Query query = ref.orderByChild("email").equalTo(email);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(SignupActivity.this, "이미 존재하는 이메일입니다.", Toast.LENGTH_SHORT).show();
                    isEmailChecked = false;
                }
                else {
                    Toast.makeText(SignupActivity.this, "사용 가능한 이메일입니다.", Toast.LENGTH_SHORT).show();
                    isEmailChecked = true;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", error.getMessage());
                Toast.makeText(SignupActivity.this, "오류가 발생했습니다: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserToDatabase(String uid, String email) {
        String dbUrl = "https://anymals-a6441-default-rtdb.firebaseio.com/";
        DatabaseReference ref = FirebaseDatabase.getInstance(dbUrl).getReference("Users");

        HashMap<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("uid", uid);

        ref.child(uid).setValue(userData).addOnCompleteListener(task -> {});
    }
}