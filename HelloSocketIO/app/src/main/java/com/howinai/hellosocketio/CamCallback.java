package com.howinai.hellosocketio;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Created by PETEroid on 9/22/16.
 */
public class CamCallback implements Camera.PreviewCallback{

    Activity activity;
    Context context;
    private static final boolean isLandscapeImage = true;

    public CamCallback(Activity a) {
        this.context = a.getApplicationContext();
        this.activity = a;
    }

    public void onPreviewFrame(final byte[] data, final Camera camera){
        // Process the camera data here
        long millis = System.currentTimeMillis();
        Log.d("Cam Callback", "shoot " + String.valueOf(millis));

        new SavePhotoTask(millis, camera.getParameters()).execute(data);

//        camera.stopPreview();
        camera.setPreviewCallback(null);
//        camera.release();
    }

    class SavePhotoTask extends AsyncTask<byte[], String, Integer> {

        private Camera.Parameters parameters;
        private File savedFile;
        private String savedFilePath;
        private long savedMillis;

        public SavePhotoTask (long l, Camera.Parameters parameters) {
            this.parameters = parameters;
            this.savedMillis = l;
        }

        @Override
        protected Integer doInBackground(byte[]... previewData) {

            try {
                Camera.Size size = parameters.getPreviewSize();

                // rotate the image before saving
                byte[] rotatedData = previewData[0];
                int rotatedHeight = size.height;
                int rotatedWidth = size.width;
                if (!CamCallback.isLandscapeImage) {
                    rotatedData = rotateYUV420Degree90(previewData[0], size.width, size.height);
                    rotatedHeight = size.width;
                    rotatedWidth = size.height;
                }
//                byte[] data = previewData[0];


                Log.d("Preview params", String.format("%d x %d, %s", size.width, size.height, parameters.getPreviewFormat()));
                YuvImage image = new YuvImage(rotatedData, parameters.getPreviewFormat(),
                        rotatedWidth, rotatedHeight, null); // swap the height and width for rotation

                File savingFolder = new File(Environment.getExternalStorageDirectory()
                        .getPath() + "/temp_pictures");
                if (!savingFolder.exists()) {
                    savingFolder.mkdirs();
                }

                savedFilePath = savingFolder.getAbsolutePath() + "/image_" + String.valueOf(savedMillis) + ".jpg";
                savedFile = new File(savedFilePath);

                if (savedFile.exists()) {
                    savedFile.delete();
                }

                FileOutputStream filecon = new FileOutputStream(savedFile);
                image.compressToJpeg(
                        new Rect(0, 0, image.getWidth(), image.getHeight()), 90,
                        filecon);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            return 1;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            Log.d("CamCallback", "post");

            if (savedFilePath == null)
                return;

            Log.d("CamCallback", "upload: " + savedFilePath);

            Future uploading = Ion.with(context)
                .load(MainActivity.SOCKET_SERVER + "/upload")
                .setMultipartFile("image", new File(savedFilePath))
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> result) {
                        try {
                            JSONObject jobj = new JSONObject(result.getResult());
                            Toast.makeText(context, jobj.getString("response"), Toast.LENGTH_SHORT).show();

                        } catch (JSONException e1) {
                            e1.printStackTrace();
                        }

                    }
                });
        }
    }

    private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight)
    {
        byte [] yuv = new byte[imageWidth*imageHeight*3/2];
        // Rotate the Y luma
        int i = 0;
        for(int x = 0;x < imageWidth;x++)
        {
            for(int y = imageHeight-1;y >= 0;y--)
            {
                yuv[i] = data[y*imageWidth+x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth*imageHeight*3/2-1;
        for(int x = imageWidth-1;x > 0;x=x-2)
        {
            for(int y = 0;y < imageHeight/2;y++)
            {
                yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+x];
                i--;
                yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
                i--;
            }
        }
        return yuv;
    }

}
