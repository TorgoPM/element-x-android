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

package io.element.android.features.location.impl.permissions

import io.element.android.libraries.architecture.Presenter

interface PermissionsPresenter : Presenter<PermissionsState> {
    interface Factory {
        fun create(permissions: List<String>): PermissionsPresenter
    }
}

sealed interface PermissionsEvents {
    object RequestPermissions : PermissionsEvents
}

data class PermissionsState(
    val permissions: Permissions = Permissions.NoneGranted,
    val shouldShowRationale: Boolean = false,
    val eventSink: (PermissionsEvents) -> Unit = {},
) {
    sealed interface Permissions {
        object AllGranted : Permissions
        object SomeGranted : Permissions
        object NoneGranted : Permissions
    }

    val isAllGranted: Boolean
        get() = permissions is Permissions.AllGranted

    val isAnyGranted: Boolean
        get() = permissions is Permissions.SomeGranted || permissions is Permissions.AllGranted

    val isNoneGranted: Boolean
        get() = permissions is Permissions.NoneGranted
}