/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.templates;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class InstallListenerTemplates {

    public static void installClickListener(View $view, final Statement $f) {
        $view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                $f.$();
            }
        });
    }

    public static void installItemClickListener(ListView $listView, final Statement $f) {
        $listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                $f.$();
            }
        });
    }

    public static void installMenuItemClick(MenuItem $menuItem, final Statement $f, final boolean $consume) {
        $menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                $f.$();
                return $consume;
            }
        });
    }

    public static void installMenuItemClick(Menu $menu, int $menuItemId, final Statement $f, final boolean $consume) {
        installMenuItemClick($menu.findItem($menuItemId), $f, $consume);
    }
}
