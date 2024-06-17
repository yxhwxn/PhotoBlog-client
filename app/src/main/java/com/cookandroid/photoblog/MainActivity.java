package com.cookandroid.photoblog;

        import androidx.activity.result.ActivityResultLauncher;
        import androidx.activity.result.contract.ActivityResultContracts; import androidx.appcompat.app.AppCompatActivity;
        import androidx.core.app.ActivityCompat;
        import androidx.core.content.ContextCompat;
        import android.Manifest;
        import android.content.Intent;
        import android.content.pm.PackageManager; import android.database.Cursor;
        import android.net.Uri;
        import android.os.Build;
        import android.os.Bundle;
        import android.os.Handler;
        import android.os.Looper;
        import android.provider.MediaStore;
        import android.util.Log;
        import android.view.View;
        import android.widget.Button;
        import android.widget.Toast;

        import com.cookandroid.photoblog.R;

        import org.json.JSONException; import org.json.JSONObject;

        import java.io.BufferedInputStream;
        import java.io.BufferedReader;
        import java.io.File;
        import java.io.FileInputStream;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.InputStreamReader;
        import java.io.OutputStream;
        import java.io.OutputStreamWriter;
        import java.io.PrintWriter;
        import java.net.HttpURLConnection;
        import java.net.MalformedURLException; import java.net.URL;
        import java.util.concurrent.ExecutorService; import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private static final int READ_MEDIA_IMAGES_PERMISSION_CODE = 1001;
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1002;

    private static final String UPLOAD_URL = "http://10.0.2.2:8000/api_root/Post/";
    Uri imageUri = null;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    String filePath = getRealPathFromURI(imageUri); executorService.execute(() -> {
                        String uploadResult;
                        uploadResult = uploadImage(filePath);
                        String finalUploadResult = uploadResult;
                        handler.post(() -> Toast.makeText(MainActivity.this, finalUploadResult, Toast.LENGTH_LONG).show());
                    });
                }
            }
    );
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button uploadButton = findViewById(R.id.uploadButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                                READ_MEDIA_IMAGES_PERMISSION_CODE);
                    } else{
                        openImagePicker();
                    }
                }else{
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            READ_EXTERNAL_STORAGE_PERMISSION_CODE); }else{
                        openImagePicker(); }
                } }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_MEDIA_IMAGES_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }
    private String getRealPathFromURI(Uri contentUri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor == null) {
            return contentUri.getPath();
        } else {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String path = cursor.getString(columnIndex);
            cursor.close();
            return path;
        }
    }

    private String uploadImage(String imagePath) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            OutputStream outputStream = null;
            PrintWriter writer = null;
            FileInputStream inputStream = null;

            try {
                File imageFile = new File(imagePath);
                String boundary = "===" + System.currentTimeMillis() + "===";
                URL url = new URL(UPLOAD_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "JWT 0512f9f20da2b3bc033f1c054171f6546bf66e22");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);

                outputStream = connection.getOutputStream();
                writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);

                // JSON 데이터 파트 추가
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("author", 1);
                jsonObject.put("title", "안드로이드-REST API 테스트");
                jsonObject.put("text", "안드로이드로 작성된 REST API 테스트 입력 입니다.");
                jsonObject.put("created_date", "2024-06-03T18:34:00+09:00");
                jsonObject.put("published_date", "2024-06-03T18:34:00+09:00");

                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"data\"").append("\r\n");
                writer.append("Content-Type: application/json; charset=UTF-8").append("\r\n");
                writer.append("\r\n").append(jsonObject.toString()).append("\r\n");
                writer.flush();

                // 이미지 파일 파트 추가
                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"" + imageFile.getName() + "\"").append("\r\n");
                writer.append("Content-Type: ").append(HttpURLConnection.guessContentTypeFromName(imageFile.getName())).append("\r\n");
                writer.append("Content-Transfer-Encoding: binary").append("\r\n");
                writer.append("\r\n").flush();

                inputStream = new FileInputStream(imageFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                inputStream.close();

                writer.append("\r\n").flush();
                writer.append("--" + boundary + "--").append("\r\n");
                writer.close();

                // 응답 확인
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    InputStream responseStream = new BufferedInputStream(connection.getInputStream());
                    BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = responseStreamReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    responseStreamReader.close();
                    String response = stringBuilder.toString();
                    Log.i("Response", response);
                } else {
                    InputStream errorStream = new BufferedInputStream(connection.getErrorStream());
                    BufferedReader errorStreamReader = new BufferedReader(new InputStreamReader(errorStream));
                    StringBuilder errorStringBuilder = new StringBuilder();
                    String line;
                    while ((line = errorStreamReader.readLine()) != null) {
                        errorStringBuilder.append(line).append("\n");
                    }
                    errorStreamReader.close();
                    String errorResponse = errorStringBuilder.toString();
                    Log.e("Error", "Server returned response code " + responseCode + ": " + errorResponse);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (writer != null) writer.close();
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return "Upload success";
    }
}