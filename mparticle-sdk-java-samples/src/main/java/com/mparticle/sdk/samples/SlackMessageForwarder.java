package com.mparticle.sdk.samples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mparticle.sdk.MessageProcessor;
import com.mparticle.sdk.model.MessageSerializer;
import com.mparticle.sdk.model.eventprocessing.*;
import com.mparticle.sdk.model.registration.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SlackMessageForwarder extends MessageProcessor {

    @Override
    public ModuleRegistrationResponse processRegistrationRequest(ModuleRegistrationRequest request) {

        ModuleRegistrationResponse response = new ModuleRegistrationResponse("SlackMessageForwarder", "1.0");

        Permissions permissions = new Permissions();

        List<DeviceIdentityPermission> deviceIds = Arrays.asList(
                new DeviceIdentityPermission(DeviceIdentity.Type.ANDROID_ID, Identity.Encoding.RAW),
                new DeviceIdentityPermission(DeviceIdentity.Type.IOS_VENDOR_ID, Identity.Encoding.RAW)
        );

        permissions.setDeviceIdentities(deviceIds);

        List<UserIdentityPermission> userIds = Arrays.asList(
                new UserIdentityPermission(UserIdentity.Type.FACEBOOK, Identity.Encoding.RAW),
                new UserIdentityPermission(UserIdentity.Type.TWITTER, Identity.Encoding.RAW),
                new UserIdentityPermission(UserIdentity.Type.EMAIL, Identity.Encoding.RAW)
        );

        permissions.setUserIdentities(userIds);

        permissions.setAllowAccessLocation(true);

        response.setPermissions(permissions);

        EventProcessingRegistration eventProcessingRegistration = new EventProcessingRegistration();

        eventProcessingRegistration.setDescription("Slack Event Forwarder");

        List<Setting> settings = new ArrayList<>();

        TextSetting endpointUrl = new TextSetting("endpointUrl", "Endpoint Url");
        endpointUrl.setIsRequired(true);
        settings.add(endpointUrl);

        TextSetting channelName = new TextSetting("channelName", "Channel Name");
        channelName.setIsRequired(true);
        settings.add(channelName);

        eventProcessingRegistration.setSettings(settings);

        List<Event.Type> supportedEventTypes = Arrays.asList(Event.Type.values());

        eventProcessingRegistration.setSupportedEventTypes(supportedEventTypes);

        eventProcessingRegistration.setMaxDataAgeHours(24);

        response.setEventProcessingRegistration(eventProcessingRegistration);

        return response;
    }

    @Override
    public EventProcessingResponse processEventProcessingRequest(EventProcessingRequest request) throws IOException {

        EventProcessingResponse response = new EventProcessingResponse();
        response.processingResults = new ArrayList<>();

        List<Event> events = request.getEvents();

        if (events != null) {
            for (Event e : events) {
                EventProcessingResult result = new EventProcessingResult(e.getId(), EventProcessingResult.Action.PROCESSED);
                response.processingResults.add(result);
            }
        }

        postMessage(request.getSubscription(), serializer.serialize(request));

        return response;
    }

    private MessageSerializer serializer = new MessageSerializer();

    private static class SlackPayload
    {
        public String channel;
        public String username;
        public String text;
        public String icon_url;
    }

    private void postMessage(ModuleSubscription subscription, String messageText) throws IOException {

        String endpointUrl = subscription.getStringSetting("endpointUrl", true, null);
        String channelName = subscription.getStringSetting("channelName", true, null);

        SlackPayload payload = new SlackPayload();
        payload.channel = channelName;
        payload.username = "LambdaForwarder";
        payload.icon_url = "http://static.mparticle.com/public/mp-icon.png";
        payload.text = messageText;

        try(CloseableHttpClient httpClient = HttpClients.createDefault()){

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(payload);

            HttpPost request = new HttpPost(endpointUrl);
            StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
            request.setEntity(entity);

            try(CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("Failed: HTTP error code: "
                            + response.getStatusLine().getStatusCode());
                }
            }
        }
    }
}
