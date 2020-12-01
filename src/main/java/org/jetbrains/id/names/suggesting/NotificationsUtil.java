package org.jetbrains.id.names.suggesting;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;

public class NotificationsUtil {
    /**
     * Checks if intellij is in the "Developer Mode" and then sends notification.
     *
     * @param project
     * @param title
     * @param context
     */
    public static void notify(Project project, String title, String context) {
        if (Registry.get("Developer Mode").asBoolean()) {
            Notifications.Bus.notify(
                    new Notification(IdNamesSuggestingBundle.message("name"),
                            title,
                            context,
                            NotificationType.INFORMATION),
                    project);
        }
    }
}