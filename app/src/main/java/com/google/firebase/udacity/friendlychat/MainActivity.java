/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2 ;
/*
    private static final String FRIENDLY_MESSAGE_LENGTH_KEY ="friendly_message_length" ;
*/
    private FirebaseDatabase mFirebaseDatabase;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mStorageReference;
    private DatabaseReference mDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mFirebaseAuthStateListener;
    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 300;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseStorage=FirebaseStorage.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
/*
        mFirebaseRemoteConfig=FirebaseRemoteConfig.getInstance();
*/
        mStorageReference=mFirebaseStorage.getReference().child("chat_photos");
        mDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);

            }
        });


        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mDatabaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });
        mFirebaseAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    onSigninInitialized(user.getDisplayName());
                } else {
                    onSignoutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

    }


    private void onSigninInitialized(String displayName) {
        mUsername = displayName;
        attachDatabaseListener();
    }

    private void onSignoutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        removeDatabaseListener();
    }

    private void removeDatabaseListener() {
        if(mChildEventListener!=null) {
            mDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    public void attachDatabaseListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            mDatabaseReference.addChildEventListener(mChildEventListener);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case R.id.sign_out_menu:
            {
                AuthUI.getInstance().signOut(this);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFirebaseAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mFirebaseAuthStateListener);
        }
        removeDatabaseListener();
        mMessageAdapter.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RC_SIGN_IN)
        {
            if(resultCode==RESULT_OK)
            {
                Toast.makeText(MainActivity.this,"Signin successfull!",Toast.LENGTH_SHORT).show();
            }
            else if(resultCode==RESULT_CANCELED)
            {
                Toast.makeText(MainActivity.this,"Signin failed!",Toast.LENGTH_SHORT).show();
                finish();
            }

        }
        else if(requestCode==RC_PHOTO_PICKER && resultCode==RESULT_OK)
        {
            assert data != null;
            Uri pickImageUri=data.getData();
            assert pickImageUri != null;
            StorageReference photoRef=mStorageReference.child(pickImageUri.getLastPathSegment());
            Toast.makeText(MainActivity.this,"Please wait for few seconds!",Toast.LENGTH_SHORT).show();
            photoRef.putFile(pickImageUri)
                    .addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            // When the image has successfully uploaded, we get its download URL
                            Task<Uri> urlTask = taskSnapshot.getStorage().getDownloadUrl();
                            while (!urlTask.isSuccessful());
                            {
                                Uri downloadUrl = urlTask.getResult();
                                String sdownload_url = String.valueOf(downloadUrl);
                                FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, sdownload_url);
                                mDatabaseReference.push().setValue(friendlyMessage);
                            }
                        }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mFirebaseAuthStateListener);
    }
}
