

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aliyun")
public class GreenImageScan {

    private String accessKeyId;
    private String secret;
    private String scenes;

    public Map imageScan(List<byte[]> imageList) throws Exception {
        IClientProfile profile = DefaultProfile
            .getProfile("cn-shanghai", accessKeyId, secret);
        DefaultProfile
            .addEndpoint("cn-shanghai", "cn-shanghai", "Green", "green.cn-shanghai.aliyuncs.com");
        IAcsClient client = new DefaultAcsClient(profile);
        ImageSyncScanRequest imageSyncScanRequest = new ImageSyncScanRequest();
        // Specify the API return format
        imageSyncScanRequest.setAcceptFormat(FormatType.JSON);
        // Specify the request method
        imageSyncScanRequest.setMethod(MethodType.POST);
        imageSyncScanRequest.setEncoding("utf-8");
        //Support HTTP and HTTPS
        imageSyncScanRequest.setProtocol(ProtocolType.HTTP);
        JSONObject httpBody = new JSONObject();
        /**
         * Set the scenes to be detected; billing is based on the scenes specified
         * In a single request, multiple images can be detected simultaneously, and each image can be checked for multiple risk scenes. 
		 Billing is calculated based on the scenes
         * For example, if 2 images are detected with the scenes specified as porn and terrorism, billing will be calculated for 
		 detecting pornography in 2 images and detecting terrorism in 2 images
         * 'porn': Indicates detection of pornographic scenes
         */

        httpBody.put("scenes", Arrays.asList(scenes.split(",")));

        /**
         * If the files you want to detect are stored on a local server, you can generate a URL using the following code snippet
         * Then pass the returned URL as the image address to the server for detection
         */
        /**
         * Set the images to be detected; each image is treated as a separate task
         * When detecting multiple images simultaneously, the processing time is determined by the last image to be completed
         * Typically, the average response time (RT) for batch detection is longer than for single-image detection. 
		 The more images submitted in a single batch, the higher the probability that the RT will be extended
         * This example demonstrates detection for a single image. For batch image detection, please create multiple tasks accordingly
         */
        ClientUploader clientUploader = ClientUploader.getImageClientUploader(profile, false);
        String url = null;
        List<JSONObject> urlList = new ArrayList<JSONObject>();
        for (byte[] bytes : imageList) {
            url = clientUploader.uploadBytes(bytes);
            JSONObject task = new JSONObject();
            task.put("dataId", UUID.randomUUID().toString());
            //Set image link to the URL after upload
            task.put("url", url);
            task.put("time", new Date());
            urlList.add(task);
        }
        httpBody.put("tasks", urlList);
        imageSyncScanRequest.setHttpContent(org.apache.commons.codec.binary.StringUtils.getBytesUtf8(httpBody.toJSONString()),
            "UTF-8", FormatType.JSON);
        /**
         * Please set the timeout. The server's end-to-end processing timeout is 10 seconds; please configure accordingly
         * If the ReadTimeout you set is shorter than the server's processing time, your program will encounter a read timeout exception
         */
        imageSyncScanRequest.setConnectTimeout(3000);
        imageSyncScanRequest.setReadTimeout(10000);
        HttpResponse httpResponse = null;
        try {
            httpResponse = client.doAction(imageSyncScanRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, String> resultMap = new HashMap<>();

        //The server receives the request, processes it, and returns the results
        if (httpResponse != null && httpResponse.isSuccess()) {
            JSONObject scrResponse = JSON.parseObject(org.apache.commons.codec.binary.StringUtils.newStringUtf8(httpResponse.getHttpContent()));
            System.out.println(JSON.toJSONString(scrResponse, true));
            int requestCode = scrResponse.getIntValue("code");
            //The detection result for each image
            JSONArray taskResults = scrResponse.getJSONArray("data");
            if (200 == requestCode) {
                for (Object taskResult : taskResults) {
                    //The processing result for a single image
                    int taskCode = ((JSONObject) taskResult).getIntValue("code");
                    //The processing results for the scenes to be detected in the image. 
					If there are multiple scenes, there will be results for each scene
                    JSONArray sceneResults = ((JSONObject) taskResult).getJSONArray("results");
                    if (200 == taskCode) {
                        for (Object sceneResult : sceneResults) {
                            String scene = ((JSONObject) sceneResult).getString("scene");
                            String label = ((JSONObject) sceneResult).getString("label");
                            String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                            //Perform relevant actions based on the scene and suggestion
                            //do something
                            System.out.println("scene = [" + scene + "]");
                            System.out.println("suggestion = [" + suggestion + "]");
                            System.out.println("suggestion = [" + label + "]");
                            if (!suggestion.equals("pass")) {
                                resultMap.put("suggestion", suggestion);
                                resultMap.put("label", label);
                                return resultMap;
                            }
                        }

                    } else {
                        //Processing of a single image failed; the reason should be analyzed in detail based on the specific situation
                        System.out.println("task process fail. task response:" + JSON.toJSONString(taskResult));
                        return null;
                    }
                }
                resultMap.put("suggestion","pass");
                return resultMap;
            } else {
                /**
                 * Indicates that the overall request processing failed; the reason should be analyzed in detail based on the specific situation
                 */
                System.out.println("the whole image scan request failed. response:" + JSON.toJSONString(scrResponse));
                return null;
            }
        }
        return null;
    }
}