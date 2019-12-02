package com.drant.FastCartMain.ui.scanitem;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.drant.FastCartMain.DownloadImageTask;
import com.drant.FastCartMain.LoginActivity;
import com.drant.FastCartMain.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.DecimalFormat;

public class ScanItemFragment extends Fragment {
    AlertDialog.Builder dialogBuilder;
    AlertDialog alertDialog;
    SurfaceView surfaceView;
    private Long scanTime;
    private CameraSource cameraSource;

    private FirebaseAuth.AuthStateListener authListener;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String product_label="PRODUCT_LABEL";
    private String product_desc="PRODUCT_DESC";
    private String product_id="PRODUCT_ID";
    private String product_image="PRODUCT_IMAGE";

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_scan_barcode, container, false);
        SurfaceView surfaceView=view.findViewById(R.id.surfaceView);
        TextView welcomeMsg= view.findViewById(R.id.welcomeMsg);
        TextView txtBarcodeValue=view.findViewById(R.id.txtBarcodeValue);
        scanTime = System.currentTimeMillis();

        // Initialize Firebase + Auth Listeners
        mAuth = FirebaseAuth.getInstance();
        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (firebaseAuth.getCurrentUser() == null) {
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                    getActivity().finish();
                }
            }
        };

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        //Profile Filling
        welcomeMsg.setText("Welcome " + mAuth.getCurrentUser().getDisplayName());

        BarcodeDetector barcodeDetector =
                new BarcodeDetector.Builder(container.getContext())
                        .setBarcodeFormats(1|2|4|5|8|32|64|128|512|1024)//QR_CODE)
                        .build();
        cameraSource = new CameraSource
                .Builder(container.getContext(), barcodeDetector)
                .setRequestedPreviewSize(1920, 1080)
                .setAutoFocusEnabled(true)
                .build();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

                try {
                    cameraSource.start(surfaceView.getHolder());
                } catch (IOException ie) {
                    Log.e("CAMERA SOURCE", ie.getMessage());
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
            }
        });

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if (barcodes.size() != 0) {
                    if (!product_desc.equals(barcodes.valueAt(0).displayValue)) {
                        txtBarcodeValue.post(new Runnable() {
                            @Override
                            public void run() {
                                //TODO: Need to lookup firebase product data and change data prior to inflating dialog view
                                product_desc = barcodes.valueAt(0).displayValue;

//                                //Build and view
//                                showAlertDialog(R.layout.product_dialog);
                            }
                        });
                    }
                }
            }
        });

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if (barcodes.size() != 0 && System.currentTimeMillis()>scanTime+2500) {
                    txtBarcodeValue.post(new Runnable() {
                        @Override
                        public void run() {
                            product_id = barcodes.valueAt(0).displayValue;
                            //Throttle
                            scanTime = System.currentTimeMillis();

                            //Vibrate
                            Vibrator v = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                            v.vibrate(200);

                            //Progress
                            final ProgressDialog progressDialog = new ProgressDialog(getActivity(), R.style.AppTheme_Light_Dialog);
                            progressDialog.setIndeterminate(true);
                            progressDialog.setMessage("Finding Product...");
                            progressDialog.show();


                            //Get Firestore Data
                            DocumentReference docRef = db.collection("products").document(product_id);
                            docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                    if (task.isSuccessful()) {
                                        DocumentSnapshot document = task.getResult();
                                        if (document.exists() && document.contains("name") && document.contains("price") && document.contains("img")) {
                                            product_label=document.getData().get("name").toString();

                                            DecimalFormat df2 = new DecimalFormat("#.00");
                                            product_desc="$"+ df2.format(document.getData().get("price"));
                                            product_image=document.getData().get("img").toString();

                                            //Build and view
                                            progressDialog.dismiss();
                                            showAlertDialog(R.layout.product_dialog);
                                        } else {
                                            progressDialog.dismiss();
                                            Toast.makeText(getActivity(),"Product Issue",Toast.LENGTH_SHORT).show();
                                            scanTime = System.currentTimeMillis()-1000;
                                        }
                                    } else {
                                        progressDialog.dismiss();
                                        Log.d("Firebase", "get failed with ", task.getException());
                                        scanTime = System.currentTimeMillis()-1000;
                                    }
                                }
                            });
                        }
                    });
                }
            }
        });

        return view;
    }

    public void showAlertDialog(int layout) {
        //Builds and inflates the dialog into view
        dialogBuilder = new AlertDialog.Builder(getActivity());
        View layoutView = getLayoutInflater().inflate(layout, null);

        //Binds
        ImageView productImage = layoutView.findViewById(R.id.productImage);
        TextView productLabel = layoutView.findViewById(R.id.productLabel);
        TextView productDesc = layoutView.findViewById(R.id.productDesc);
        Button productButton = layoutView.findViewById(R.id.productButton);

        //Set data
        productLabel.setText(product_label);
        productDesc.setText(product_desc);
        new DownloadImageTask(productImage).execute(product_image);

        dialogBuilder.setView(layoutView);
        alertDialog = dialogBuilder.create();
        alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        scanTime = System.currentTimeMillis();
        alertDialog.show();

        //TODO: Make productButton cancel current addition to cart
        productButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.dismiss();
                Toast.makeText(getActivity(), "Removed Item From Cart", Toast.LENGTH_SHORT).show();
            }
        });

        //Runnable to dismiss alert dialog
        final Runnable closeDialog = new Runnable() {
            @Override
            public void run() {
                if (alertDialog.isShowing()) {
                    alertDialog.dismiss();
                }
            }
        };

        //Handler to execute ^runnable after delay, closes further thread callbacks
        final Handler handler = new Handler();
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                handler.removeCallbacks(closeDialog);
                scanTime = System.currentTimeMillis()-2500;
            }
        });
        handler.postDelayed(closeDialog, 2000);
    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (authListener != null) {
//            mAuth.removeAuthStateListener(authListener);
//        }
//        cameraSource.release();
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (authListener != null) {
//            mAuth.addAuthStateListener(authListener);
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        mAuth.signOut();
//    }

}