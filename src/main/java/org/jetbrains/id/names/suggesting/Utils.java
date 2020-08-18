package org.jetbrains.id.names.suggesting;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;

public class Utils {
    public static void notify(Project project, String title, String context){
        Notifications.Bus.notify(
                new Notification(IdNamesSuggestingBundle.message("name"),
                        title,
                        context,
                        NotificationType.INFORMATION),
                project);
    }
}
