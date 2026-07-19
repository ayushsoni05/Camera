package com.example.camera;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";

    private FrameLayout authContainer;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // Temporary registration states
    private String firstName = "";
    private String lastName = "";
    private String birthday = "";
    private int birthYear, birthMonth, birthDay;
    private String username = "";
    private String password = "";
    private String email = "";

    // Username checking state
    private boolean isUsernameAvailable = false;
    private Handler usernameCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable usernameCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        authContainer = findViewById(R.id.auth_container);
        mAuth = FirebaseSafeHelper.getAuth();
        mDatabase = FirebaseSafeHelper.getDatabaseReference();

        // Check if user is already logged in (Firebase or Local)
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        android.content.SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        boolean isLocalLoggedIn = prefs.getString("current_user_uid", null) != null;
        
        if (currentUser != null || isLocalLoggedIn) {
            launchMainActivity();
            return;
        }

        // Show welcome view by default
        showWelcomeView(true);
    }

    private void launchMainActivity() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void transitionToView(View newView, boolean isForward) {
        if (authContainer.getChildCount() > 0) {
            View oldView = authContainer.getChildAt(0);
            
            Animation outAnim = AnimationUtils.loadAnimation(this, isForward ? R.anim.slide_out_left : R.anim.slide_out_right);
            Animation inAnim = AnimationUtils.loadAnimation(this, isForward ? R.anim.slide_in_right : R.anim.slide_in_left);
            
            oldView.startAnimation(outAnim);
            authContainer.removeView(oldView);
            
            authContainer.addView(newView);
            newView.startAnimation(inAnim);
        } else {
            authContainer.addView(newView);
        }
    }

    private void showWelcomeView(boolean isForward) {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_auth_welcome, authContainer, false);
        
        view.findViewById(R.id.btn_welcome_login).setOnClickListener(v -> showLoginView());
        view.findViewById(R.id.btn_welcome_signup).setOnClickListener(v -> showSignupNameView());
        
        transitionToView(view, isForward);
    }

    private void showLoginView() {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_auth_login, authContainer, false);
        
        view.findViewById(R.id.btn_login_back).setOnClickListener(v -> showWelcomeView(false));
        
        EditText etUsername = view.findViewById(R.id.et_login_username);
        EditText etPassword = view.findViewById(R.id.et_login_password);
        ImageButton btnToggle = view.findViewById(R.id.btn_login_pw_toggle);
        
        // Password toggle visibility
        final boolean[] isPasswordVisible = {false};
        btnToggle.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggle.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            isPasswordVisible[0] = !isPasswordVisible[0];
            etPassword.setSelection(etPassword.getText().length());
        });

        view.findViewById(R.id.btn_login_submit).setOnClickListener(v -> {
            String input = etUsername.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            if (input.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter your credentials.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (input.contains("@")) {
                // Login via email directly
                loginWithEmail(input, pass);
            } else {
                // Login via username
                loginWithUsername(input, pass);
            }
        });

        transitionToView(view, true);
    }

    private void loginWithEmail(String emailInput, String pass) {
        if (mAuth == null) {
            Log.e(TAG, "mAuth is null, trying local login");
            if (loginLocally(emailInput, pass)) {
                Toast.makeText(this, "Logged in offline mode... 💾", Toast.LENGTH_SHORT).show();
                launchMainActivity();
            } else {
                Toast.makeText(AuthActivity.this, "Authentication failed: Firebase unconfigured and local user not found.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        mAuth.signInWithEmailAndPassword(emailInput, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        launchMainActivity();
                    } else {
                        Log.e(TAG, "Firebase login failed, trying local login", task.getException());
                        if (loginLocally(emailInput, pass)) {
                            Toast.makeText(this, "Logged in offline mode... 💾", Toast.LENGTH_SHORT).show();
                            launchMainActivity();
                        } else {
                            Toast.makeText(AuthActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void loginWithUsername(String usernameInput, String pass) {
        if (loginLocally(usernameInput, pass)) {
            Toast.makeText(this, "Logged in offline mode... 💾", Toast.LENGTH_SHORT).show();
            launchMainActivity();
            return;
        }

        if (mDatabase == null) {
            Toast.makeText(this, "Authentication failed: Database unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        mDatabase.child("usernames").child(usernameInput.toLowerCase()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String uid = snapshot.getValue(String.class);
                    if (uid != null) {
                        // Look up email associated with UID
                        mDatabase.child("users").child(uid).child("email").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot emailSnapshot) {
                                String userEmail = emailSnapshot.getValue(String.class);
                                if (userEmail != null) {
                                    loginWithEmail(userEmail, pass);
                                } else {
                                    Toast.makeText(AuthActivity.this, "Account email not found.", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(AuthActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    Toast.makeText(AuthActivity.this, "Username not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AuthActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSignupNameView() {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_signup_name, authContainer, false);
        
        view.findViewById(R.id.btn_signup_name_back).setOnClickListener(v -> showWelcomeView(false));
        
        EditText etFirst = view.findViewById(R.id.et_signup_firstname);
        EditText etLast = view.findViewById(R.id.et_signup_lastname);
        
        etFirst.setText(firstName);
        etLast.setText(lastName);

        view.findViewById(R.id.btn_signup_name_next).setOnClickListener(v -> {
            firstName = etFirst.getText().toString().trim();
            lastName = etLast.getText().toString().trim();

            if (firstName.isEmpty() || lastName.isEmpty()) {
                Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show();
                return;
            }

            showSignupBirthdayView();
        });

        transitionToView(view, true);
    }

    private void showSignupBirthdayView() {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_signup_birthday, authContainer, false);
        
        view.findViewById(R.id.btn_signup_birthday_back).setOnClickListener(v -> showSignupNameView());
        
        TextView tvDisplay = view.findViewById(R.id.tv_signup_birthday_display);
        DatePicker dp = view.findViewById(R.id.dp_signup_birthday);

        // Configure default date to 18 years ago or saved birthdate
        Calendar cal = Calendar.getInstance();
        if (birthday.isEmpty()) {
            cal.add(Calendar.YEAR, -18);
            birthYear = cal.get(Calendar.YEAR);
            birthMonth = cal.get(Calendar.MONTH);
            birthDay = cal.get(Calendar.DAY_OF_MONTH);
        }

        dp.init(birthYear, birthMonth, birthDay, (view1, year, monthOfYear, dayOfMonth) -> {
            birthYear = year;
            birthMonth = monthOfYear;
            birthDay = dayOfMonth;

            Calendar selected = Calendar.getInstance();
            selected.set(year, monthOfYear, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            birthday = sdf.format(selected.getTime());
            tvDisplay.setText(birthday);
        });

        // Initialize display value
        Calendar initSel = Calendar.getInstance();
        initSel.set(birthYear, birthMonth, birthDay);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
        birthday = sdf.format(initSel.getTime());
        tvDisplay.setText(birthday);

        view.findViewById(R.id.btn_signup_birthday_next).setOnClickListener(v -> {
            // Verify age is at least 13
            Calendar today = Calendar.getInstance();
            Calendar dob = Calendar.getInstance();
            dob.set(birthYear, birthMonth, birthDay);
            
            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }

            if (age < 13) {
                Toast.makeText(this, "Sorry, you must be at least 13 to join SnapTake.", Toast.LENGTH_LONG).show();
                return;
            }

            showSignupUsernameView();
        });

        transitionToView(view, true);
    }

    private void showSignupUsernameView() {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_signup_username, authContainer, false);
        
        view.findViewById(R.id.btn_signup_username_back).setOnClickListener(v -> showSignupBirthdayView());
        
        EditText etUsername = view.findViewById(R.id.et_signup_username);
        TextView tvStatus = view.findViewById(R.id.tv_username_availability_status);
        View btnNext = view.findViewById(R.id.btn_signup_username_next);

        etUsername.setText(username);
        isUsernameAvailable = false;

        etUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isUsernameAvailable = false;
                btnNext.setEnabled(false);
                
                if (usernameCheckRunnable != null) {
                    usernameCheckHandler.removeCallbacks(usernameCheckRunnable);
                }

                String typed = s.toString().trim().toLowerCase();
                if (typed.length() < 3) {
                    tvStatus.setText("Username must be at least 3 characters");
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    return;
                }

                tvStatus.setText("Checking availability...");
                tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));

                usernameCheckRunnable = () -> checkUsernameAvailability(typed, tvStatus, btnNext);
                usernameCheckHandler.postDelayed(usernameCheckRunnable, 400); // 400ms debounce
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnNext.setOnClickListener(v -> {
            username = etUsername.getText().toString().trim();
            if (isUsernameAvailable) {
                showSignupPasswordView();
            } else {
                Toast.makeText(this, "Please choose an available username.", Toast.LENGTH_SHORT).show();
            }
        });

        // Trigger check initially if username state exists
        if (!username.isEmpty()) {
            checkUsernameAvailability(username.toLowerCase(), tvStatus, btnNext);
        } else {
            btnNext.setEnabled(false);
        }

        transitionToView(view, true);
    }

    private void checkUsernameAvailability(String checkedUsername, TextView tvStatus, View btnNext) {
        android.content.SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        if (prefs.contains("local_username_" + checkedUsername)) {
            tvStatus.setText("Username is already taken");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            isUsernameAvailable = false;
            btnNext.setEnabled(false);
            return;
        }

        if (mDatabase == null) {
            // Offline fallback
            isUsernameAvailable = true;
            runOnUiThread(() -> {
                tvStatus.setText("Database unavailable. Defaulting to available.");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                btnNext.setEnabled(true);
            });
            return;
        }

        mDatabase.child("usernames").child(checkedUsername).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    tvStatus.setText("Username is already taken");
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    isUsernameAvailable = false;
                    btnNext.setEnabled(false);
                } else {
                    tvStatus.setText("Username is available!");
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    isUsernameAvailable = true;
                    btnNext.setEnabled(true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Username query cancelled", error.toException());
            }
        });
    }

    private void showSignupPasswordView() {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_signup_password, authContainer, false);
        
        view.findViewById(R.id.btn_signup_password_back).setOnClickListener(v -> showSignupUsernameView());
        
        EditText etPassword = view.findViewById(R.id.et_signup_password);
        ImageButton btnToggle = view.findViewById(R.id.btn_signup_pw_toggle);

        etPassword.setText(password);

        final boolean[] isPasswordVisible = {false};
        btnToggle.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggle.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            isPasswordVisible[0] = !isPasswordVisible[0];
            etPassword.setSelection(etPassword.getText().length());
        });

        view.findViewById(R.id.btn_signup_password_next).setOnClickListener(v -> {
            password = etPassword.getText().toString().trim();
            if (password.length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters.", Toast.LENGTH_SHORT).show();
                return;
            }
            showSignupEmailView();
        });

        transitionToView(view, true);
    }

    private void showSignupEmailView() {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_signup_email, authContainer, false);
        
        view.findViewById(R.id.btn_signup_email_back).setOnClickListener(v -> showSignupPasswordView());
        
        EditText etEmail = view.findViewById(R.id.et_signup_email);
        etEmail.setText(email);

        view.findViewById(R.id.btn_signup_email_next).setOnClickListener(v -> {
            email = etEmail.getText().toString().trim();

            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
                return;
            }

            performUserRegistration();
        });

        transitionToView(view, true);
    }

    private void performUserRegistration() {
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show();
        
        if (mAuth == null) {
            Log.e(TAG, "mAuth is null, registering locally");
            String localUid = "local_" + System.currentTimeMillis();
            saveUserLocally(localUid, firstName, lastName, birthday, username, email, password);
            showFirebaseConfigDialog();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserDataToDatabase(user.getUid());
                        }
                    } else {
                        Log.e(TAG, "Firebase registration failed, registering locally", task.getException());
                        String localUid = "local_" + System.currentTimeMillis();
                        saveUserLocally(localUid, firstName, lastName, birthday, username, email, password);
                        
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "";
                        if (errorMsg.contains("configuration not found") || errorMsg.contains("Configuration not found") || errorMsg.contains("internal error") || errorMsg.contains("Internal error")) {
                            showFirebaseConfigDialog();
                        } else {
                            Toast.makeText(AuthActivity.this, "Firebase Registration Failed: " + errorMsg + "\nProceeding offline... 💾", Toast.LENGTH_LONG).show();
                            launchMainActivity();
                        }
                    }
                });
    }

    private void showFirebaseConfigDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Firebase Config Needed 🛠️")
            .setMessage("We detected that Firebase Auth is not fully configured for this app.\n\n" +
                        "To fix this:\n" +
                        "1. Open your Firebase Console (project: snaptake-5a82f).\n" +
                        "2. Go to Build ➔ Authentication ➔ Sign-in method.\n" +
                        "3. Enable the 'Email/Password' provider.\n\n" +
                        "For now, we have successfully logged you in via Offline/Local Mode! 💾")
            .setPositiveButton("Continue Offline", (dialog, which) -> launchMainActivity())
            .setCancelable(false)
            .show();
    }

    private void saveUserDataToDatabase(String uid) {
        if (mDatabase == null) {
            // Local bypass if database is missing offline
            launchMainActivity();
            return;
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", uid);
        userMap.put("firstName", firstName);
        userMap.put("lastName", lastName);
        userMap.put("birthday", birthday);
        userMap.put("username", username);
        userMap.put("email", email);

        mDatabase.child("users").child(uid).setValue(userMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Save unique username lookup mapping
                        mDatabase.child("usernames").child(username.toLowerCase()).setValue(uid)
                                .addOnCompleteListener(task1 -> launchMainActivity());
                    } else {
                        Log.e(TAG, "Database save error", task.getException());
                        Toast.makeText(AuthActivity.this, "Database save error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        // Proceed to main screen anyway so user is not stuck in signup loop
                        launchMainActivity();
                    }
                });
    }

    private void saveUserLocally(String uid, String firstName, String lastName, String birthday, String username, String email, String password) {
        android.content.SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("local_username_" + username.toLowerCase(), uid);
        editor.putString("local_email_" + email.toLowerCase(), uid);
        editor.putString("local_password_" + uid, password);
        editor.putString("local_firstname_" + uid, firstName);
        editor.putString("local_lastname_" + uid, lastName);
        editor.putString("local_birthday_" + uid, birthday);
        editor.putString("current_user_uid", uid);
        editor.putString("current_user_username", username);
        editor.putString("current_user_email", email);
        
        editor.apply();
    }

    private boolean loginLocally(String usernameOrEmail, String enteredPassword) {
        android.content.SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        String key = usernameOrEmail.toLowerCase();
        
        String uid = prefs.getString("local_username_" + key, null);
        if (uid == null) {
            uid = prefs.getString("local_email_" + key, null);
        }
        
        if (uid != null) {
            String correctPassword = prefs.getString("local_password_" + uid, null);
            if (enteredPassword.equals(correctPassword)) {
                prefs.edit().putString("current_user_uid", uid)
                            .putString("current_user_username", prefs.getString("local_username_" + uid, ""))
                            .putString("current_user_email", prefs.getString("local_email_" + uid, ""))
                            .apply();
                return true;
            }
        }
        return false;
    }
}
