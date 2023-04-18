/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.libraries.push.impl.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.asRoomId
import io.element.android.libraries.matrix.api.core.asSessionId
import io.element.android.libraries.matrix.api.core.asThreadId
import io.element.android.libraries.push.impl.log.notificationLoggerTag
import io.element.android.services.analytics.api.AnalyticsTracker
import io.element.android.services.toolbox.api.systemclock.SystemClock
import timber.log.Timber
import javax.inject.Inject

private val loggerTag = LoggerTag("NotificationBroadcastReceiver", notificationLoggerTag)

/**
 * Receives actions broadcast by notification (on click, on dismiss, inline replies, etc.).
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationDrawerManager: NotificationDrawerManager

    //@Inject lateinit var activeSessionHolder: ActiveSessionHolder
    @Inject lateinit var analyticsTracker: AnalyticsTracker
    @Inject lateinit var clock: SystemClock
    @Inject lateinit var actionIds: NotificationActionIds

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        context.bindings<NotificationBroadcastReceiverBindings>().inject(this)
        Timber.tag(loggerTag.value).v("NotificationBroadcastReceiver received : $intent")
        val sessionId = intent.extras?.getString(KEY_SESSION_ID)?.asSessionId() ?: return
        when (intent.action) {
            actionIds.smartReply ->
                handleSmartReply(intent, context)
            actionIds.dismissRoom ->
                intent.getStringExtra(KEY_ROOM_ID)?.asRoomId()?.let { roomId ->
                    notificationDrawerManager.clearMessagesForRoom(sessionId, roomId)
                }
            actionIds.dismissSummary ->
                notificationDrawerManager.clearAllEvents(sessionId)
            actionIds.markRoomRead ->
                intent.getStringExtra(KEY_ROOM_ID)?.asRoomId()?.let { roomId ->
                    notificationDrawerManager.clearMessagesForRoom(sessionId, roomId)
                    handleMarkAsRead(sessionId, roomId)
                }
            actionIds.join -> {
                intent.getStringExtra(KEY_ROOM_ID)?.asRoomId()?.let { roomId ->
                    notificationDrawerManager.clearMemberShipNotificationForRoom(sessionId, roomId)
                    handleJoinRoom(sessionId, roomId)
                }
            }
            actionIds.reject -> {
                intent.getStringExtra(KEY_ROOM_ID)?.asRoomId()?.let { roomId ->
                    notificationDrawerManager.clearMemberShipNotificationForRoom(sessionId, roomId)
                    handleRejectRoom(sessionId, roomId)
                }
            }
        }
    }

    private fun handleJoinRoom(sessionId: SessionId, roomId: RoomId) {
        /*
        activeSessionHolder.getSafeActiveSession()?.let { session ->
            val room = session.getRoom(roomId)
            if (room != null) {
                session.coroutineScope.launch {
                    tryOrNull {
                        session.roomService().joinRoom(room.roomId)
                        analyticsTracker.capture(room.roomSummary().toAnalyticsJoinedRoom(JoinedRoom.Trigger.Notification))
                    }
                }
            }
        }

         */
    }

    private fun handleRejectRoom(sessionId: SessionId, roomId: RoomId) {
        /*
        activeSessionHolder.getSafeActiveSession()?.let { session ->
            session.coroutineScope.launch {
                tryOrNull { session.roomService().leaveRoom(roomId) }
            }
        }

         */
    }

    private fun handleMarkAsRead(sessionId: SessionId, roomId: RoomId) {
        /*
        activeSessionHolder.getActiveSession().let { session ->
            val room = session.getRoom(roomId)
            if (room != null) {
                session.coroutineScope.launch {
                    tryOrNull { room.readService().markAsRead(ReadService.MarkAsReadParams.READ_RECEIPT, mainTimeLineOnly = false) }
                }
            }
        }

         */
    }

    private fun handleSmartReply(intent: Intent, context: Context) {
        val message = getReplyMessage(intent)
        val sessionId = intent.getStringExtra(KEY_SESSION_ID)?.asSessionId()
        val roomId = intent.getStringExtra(KEY_ROOM_ID)?.asRoomId()
        val threadId = intent.getStringExtra(KEY_THREAD_ID)?.asThreadId()

        if (message.isNullOrBlank() || roomId == null) {
            // ignore this event
            // Can this happen? should we update notification?
            return
        }
        /*
        activeSessionHolder.getActiveSession().let { session ->
            session.getRoom(roomId)?.let { room ->
                sendMatrixEvent(message, threadId, session, room, context)
            }
        }

         */
    }

    /*
    private fun sendMatrixEvent(message: String, threadId: String?, session: Session, room: Room, context: Context?) {
        if (threadId != null) {
            room.relationService().replyInThread(
                    rootThreadEventId = threadId,
                    replyInThreadText = message,
            )
        } else {
            room.sendService().sendTextMessage(message)
        }

        // Create a new event to be displayed in the notification drawer, right now

        val notifiableMessageEvent = NotifiableMessageEvent(
                // Generate a Fake event id
                eventId = UUID.randomUUID().toString(),
                editedEventId = null,
                noisy = false,
                timestamp = clock.epochMillis(),
                senderName = session.roomService().getRoomMember(session.myUserId, room.roomId)?.displayName
                        ?: context?.getString(R.string.notification_sender_me),
                senderId = session.myUserId,
                body = message,
                imageUriString = null,
                roomId = room.roomId,
                threadId = threadId,
                roomName = room.roomSummary()?.displayName ?: room.roomId,
                roomIsDirect = room.roomSummary()?.isDirect == true,
                outGoingMessage = true,
                canBeReplaced = false
        )

        notificationDrawerManager.updateEvents { it.onNotifiableEventReceived(notifiableMessageEvent) }

        /*
        // TODO Error cannot be managed the same way than in Riot

        val event = Event(mxMessage, session.credentials.userId, roomId)
        room.storeOutgoingEvent(event)
        room.sendEvent(event, object : MatrixCallback<Void?> {
            override fun onSuccess(info: Void?) {
                Timber.v("Send message : onSuccess ")
            }

            override fun onNetworkError(e: Exception) {
                Timber.e(e, "Send message : onNetworkError")
                onSmartReplyFailed(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                Timber.v("Send message : onMatrixError " + e.message)
                if (e is MXCryptoError) {
                    Toast.makeText(context, e.detailedErrorDescription, Toast.LENGTH_SHORT).show()
                    onSmartReplyFailed(e.detailedErrorDescription)
                } else {
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    onSmartReplyFailed(e.localizedMessage)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                Timber.e(e, "Send message : onUnexpectedError " + e.message)
                onSmartReplyFailed(e.message)
            }


            fun onSmartReplyFailed(reason: String?) {
                val notifiableMessageEvent = NotifiableMessageEvent(
                        event.eventId,
                        false,
                        clock.epochMillis(),
                        session.myUser?.displayname
                                ?: context?.getString(R.string.notification_sender_me),
                        session.myUserId,
                        message,
                        roomId,
                        room.getRoomDisplayName(context),
                        room.isDirect)
                notifiableMessageEvent.outGoingMessage = true
                notifiableMessageEvent.outGoingMessageFailed = true

                VectorApp.getInstance().notificationDrawerManager.onNotifiableEventReceived(notifiableMessageEvent)
                VectorApp.getInstance().notificationDrawerManager.refreshNotificationDrawer(null)
            }
        })
         */
    }

     */

    private fun getReplyMessage(intent: Intent?): String? {
        if (intent != null) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            if (remoteInput != null) {
                return remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()
            }
        }
        return null
    }

    companion object {
        const val KEY_SESSION_ID = "sessionID"
        const val KEY_ROOM_ID = "roomID"
        const val KEY_THREAD_ID = "threadID"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}