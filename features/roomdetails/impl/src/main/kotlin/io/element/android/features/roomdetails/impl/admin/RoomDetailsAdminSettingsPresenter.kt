/*
 * Copyright (c) 2024 New Vector Ltd
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

package io.element.android.features.roomdetails.impl.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.room.MatrixRoom
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.roomMembers
import javax.inject.Inject

class RoomDetailsAdminSettingsPresenter @Inject constructor(
    private val room: MatrixRoom,
) : Presenter<RoomDetailsAdminSettingsState> {
    @Composable
    override fun present(): RoomDetailsAdminSettingsState {
        val members by room.membersStateFlow.collectAsState()
        val adminCount by remember {
            derivedStateOf {
                members.roomMembers().orEmpty().count { it.role == RoomMember.Role.ADMIN }
            }
        }
        val moderatorCount by remember {
            derivedStateOf {
                members.roomMembers().orEmpty().count { it.role == RoomMember.Role.MODERATOR }
            }
        }
        return RoomDetailsAdminSettingsState(
            adminCount = adminCount,
            moderatorCount = moderatorCount,
        )
    }
}
