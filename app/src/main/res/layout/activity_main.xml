<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/bg_main_gradient">

    <!-- 第一层：主界面 -->
    <LinearLayout
        android:id="@+id/layout_main_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="8dp">

        <!-- 顶栏：标题、居中放大镜搜索图标、服务器设置与圆形关闭图标 -->
        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="40dp"
            android:gravity="center_vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentLeft="true"
                android:layout_centerVertical="true"
                android:text="Subsonic Player (v1.0.2)"
                android:textColor="#ffffff"
                android:textSize="18sp"
                android:textStyle="bold" />

            <!-- 首页顶部居中：放大镜搜索图标按钮 -->
            <ImageView
                android:id="@+id/btn_top_search"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:layout_centerInParent="true"
                android:background="@drawable/bg_btn_circle_default"
                android:scaleType="center"
                android:clickable="true"
                android:focusable="true" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentRight="true"
                android:layout_centerVertical="true"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <Button
                    android:id="@+id/btn_toggle_config"
                    android:layout_width="wrap_content"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="服务器设置"
                    android:textColor="#e0e0e0"
                    android:textSize="12sp"
                    android:paddingLeft="10dp"
                    android:paddingRight="10dp"
                    android:layout_marginRight="8dp" />

                <!-- 关闭软件：圆形图标按钮 -->
                <ImageView
                    android:id="@+id/btn_exit_app"
                    android:layout_width="34dp"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_danger_circle"
                    android:scaleType="center"
                    android:clickable="true"
                    android:focusable="true" />
            </LinearLayout>
        </RelativeLayout>

        <!-- 可折叠服务器配置面板 -->
        <LinearLayout
            android:id="@+id/layout_config_panel"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:background="@drawable/bg_card"
            android:padding="10dp"
            android:layout_marginTop="4dp"
            android:clickable="true"
            android:focusable="true"
            android:visibility="gone">

            <EditText
                android:id="@+id/et_server"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="服务器地址 (如 http://192.168.1.100:4533)"
                android:textColor="#ffffff"
                android:textColorHint="#777777"
                android:singleLine="true" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="4dp">

                <EditText
                    android:id="@+id/et_username"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:hint="用户名"
                    android:textColor="#ffffff"
                    android:textColorHint="#777777"
                    android:singleLine="true" />

                <EditText
                    android:id="@+id/et_password"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:hint="密码"
                    android:textColor="#ffffff"
                    android:textColorHint="#777777"
                    android:inputType="textPassword"
                    android:singleLine="true" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginTop="6dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="超时时长(秒):"
                    android:textColor="#cccccc"
                    android:textSize="13sp" />

                <EditText
                    android:id="@+id/et_timeout_sec"
                    android:layout_width="55dp"
                    android:layout_height="wrap_content"
                    android:text="20"
                    android:hint="20"
                    android:textColor="#ffffff"
                    android:textColorHint="#777777"
                    android:inputType="number"
                    android:gravity="center"
                    android:singleLine="true"
                    android:layout_marginLeft="4dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="重试次数:"
                    android:textColor="#cccccc"
                    android:textSize="13sp"
                    android:layout_marginLeft="14dp" />

                <EditText
                    android:id="@+id/et_retry_count"
                    android:layout_width="50dp"
                    android:layout_height="wrap_content"
                    android:text="3"
                    android:hint="3"
                    android:textColor="#ffffff"
                    android:textColorHint="#777777"
                    android:inputType="number"
                    android:gravity="center"
                    android:singleLine="true"
                    android:layout_marginLeft="4dp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginTop="6dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="下载保存路径:"
                    android:textColor="#cccccc"
                    android:textSize="13sp" />

                <EditText
                    android:id="@+id/et_download_path"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:hint="/sdcard/Music"
                    android:textColor="#ffffff"
                    android:textColorHint="#777777"
                    android:singleLine="true"
                    android:layout_marginLeft="4dp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginTop="6dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="默认播放码率:"
                    android:textColor="#cccccc"
                    android:textSize="13sp" />

                <Spinner
                    android:id="@+id/spinner_config_bitrate"
                    android:layout_width="wrap_content"
                    android:layout_height="32dp"
                    android:background="@drawable/bg_btn_pill"
                    android:paddingLeft="10dp"
                    android:paddingRight="10dp"
                    android:layout_marginLeft="8dp"
                    android:spinnerMode="dropdown" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginTop="6dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="缓存上限(MB):"
                    android:textColor="#cccccc"
                    android:textSize="13sp" />

                <EditText
                    android:id="@+id/et_cache_size"
                    android:layout_width="65dp"
                    android:layout_height="wrap_content"
                    android:text="500"
                    android:hint="500"
                    android:textColor="#ffffff"
                    android:textColorHint="#777777"
                    android:inputType="number"
                    android:gravity="center"
                    android:singleLine="true"
                    android:layout_marginLeft="4dp" />

                <Button
                    android:id="@+id/btn_clear_cache"
                    android:layout_width="wrap_content"
                    android:layout_height="36dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="清理缓存"
                    android:textSize="12sp"
                    android:textColor="#e0e0e0"
                    android:paddingLeft="12dp"
                    android:paddingRight="12dp"
                    android:layout_marginLeft="8dp" />

                <View
                    android:layout_width="0dp"
                    android:layout_height="1dp"
                    android:layout_weight="1" />

                <Button
                    android:id="@+id/btn_connect"
                    android:layout_width="wrap_content"
                    android:layout_height="38dp"
                    android:background="@drawable/bg_btn_accent"
                    android:text="保存并连接"
                    android:textColor="#00e5ff"
                    android:textSize="13sp"
                    android:textStyle="bold"
                    android:paddingLeft="16dp"
                    android:paddingRight="16dp"
                    android:gravity="center" />
            </LinearLayout>
        </LinearLayout>

        <!-- 导航切换：我的歌单 与 排行榜 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="38dp"
            android:orientation="horizontal"
            android:layout_marginTop="6dp">

            <Button
                android:id="@+id/btn_tab_playlists"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="@drawable/bg_btn_default"
                android:text="我的歌单"
                android:textColor="#00e5ff"
                android:textSize="13sp" />

            <Button
                android:id="@+id/btn_tab_ranking"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="@drawable/bg_btn_default"
                android:text="排行榜"
                android:textColor="#a0a5b5"
                android:textSize="13sp"
                android:layout_marginLeft="6dp" />
        </LinearLayout>

        <!-- 展开的搜索栏：左侧带分类选择 -->
        <LinearLayout
            android:id="@+id/layout_search_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginTop="6dp"
            android:visibility="gone">

            <Spinner
                android:id="@+id/spinner_search_type"
                android:layout_width="wrap_content"
                android:layout_height="34dp"
                android:background="@drawable/bg_btn_pill"
                android:paddingLeft="8dp"
                android:paddingRight="8dp"
                android:layout_marginRight="6dp"
                android:spinnerMode="dropdown" />

            <EditText
                android:id="@+id/et_search_keyword"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="输入歌曲名检索..."
                android:textColor="#ffffff"
                android:textColorHint="#777777"
                android:singleLine="true" />

            <Button
                android:id="@+id/btn_search_submit"
                android:layout_width="wrap_content"
                android:layout_height="38dp"
                android:background="@drawable/bg_btn_accent"
                android:text="搜索"
                android:textColor="#00e5ff"
                android:textSize="13sp"
                android:paddingLeft="14dp"
                android:paddingRight="14dp"
                android:layout_marginLeft="6dp" />
        </LinearLayout>

        <!-- 主体列表与侧边队列 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:orientation="horizontal"
            android:layout_marginTop="6dp">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:orientation="vertical">

                <RelativeLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="4dp">

                    <TextView
                        android:id="@+id/tv_list_title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_centerVertical="true"
                        android:text="我的歌单"
                        android:textColor="#a0a5b5"
                        android:textSize="14sp" />

                    <Button
                        android:id="@+id/btn_back"
                        android:layout_width="wrap_content"
                        android:layout_height="30dp"
                        android:layout_alignParentRight="true"
                        android:layout_centerVertical="true"
                        android:background="@drawable/bg_btn_default"
                        android:text="返回列表"
                        android:textColor="#e0e0e0"
                        android:textSize="11sp"
                        android:paddingLeft="8dp"
                        android:paddingRight="8dp"
                        android:visibility="gone" />
                </RelativeLayout>

                <ListView
                    android:id="@+id/list_view"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:divider="#242730"
                    android:dividerHeight="1dp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/layout_queue_panel"
                android:layout_width="290dp"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:background="@drawable/bg_card"
                android:padding="8dp"
                android:layout_marginLeft="6dp"
                android:clickable="true"
                android:focusable="true"
                android:visibility="gone">

                <RelativeLayout
                    android:layout_width="match_parent"
                    android:layout_height="40dp"
                    android:gravity="center_vertical"
                    android:layout_marginBottom="4dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_centerVertical="true"
                        android:text="当前播放列表"
                        android:textColor="#ffffff"
                        android:textSize="14sp"
                        android:textStyle="bold" />

                    <Button
                        android:id="@+id/btn_close_queue"
                        android:layout_width="wrap_content"
                        android:layout_height="32dp"
                        android:layout_alignParentRight="true"
                        android:layout_centerVertical="true"
                        android:background="@drawable/bg_btn_default"
                        android:text="关闭"
                        android:textColor="#e0e0e0"
                        android:textSize="12sp"
                        android:paddingLeft="12dp"
                        android:paddingRight="12dp" />
                </RelativeLayout>

                <ListView
                    android:id="@+id/lv_queue"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:divider="#333842"
                    android:dividerHeight="1dp" />
            </LinearLayout>

        </LinearLayout>

        <!-- 底部常驻播放器条：模式按键与音效按键均为 36dp 纯净扁平图标 -->
        <LinearLayout
            android:id="@+id/layout_bottom_player"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:background="@drawable/bg_card"
            android:padding="6dp"
            android:gravity="center_vertical"
            android:layout_marginTop="6dp"
            android:clickable="true"
            android:focusable="true">

            <LinearLayout
                android:layout_width="126dp"
                android:layout_height="48dp"
                android:gravity="left|center_vertical">

                <Button
                    android:id="@+id/btn_open_detail"
                    android:layout_width="wrap_content"
                    android:layout_height="44dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="歌词/详情"
                    android:textColor="#e0e0e0"
                    android:textSize="12sp"
                    android:paddingLeft="10dp"
                    android:paddingRight="10dp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:paddingLeft="8dp"
                android:paddingRight="8dp">

                <TextView
                    android:id="@+id/tv_current_song"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="未在播放"
                    android:textColor="#ffffff"
                    android:textSize="13sp"
                    android:textStyle="bold"
                    android:singleLine="true" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <SeekBar
                        android:id="@+id/seek_bar"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:max="100" />

                    <TextView
                        android:id="@+id/tv_time"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="00:00 / 00:00"
                        android:textColor="#888c99"
                        android:textSize="11sp"
                        android:paddingLeft="4dp" />
                </LinearLayout>

                <!-- 底栏对称控制 Dock：模式与音效均为 36dp 纯净扁平图标 -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="2dp">

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:gravity="right|center_vertical"
                        android:orientation="horizontal">

                        <!-- 底部模式图标：完全透明无框扁平 (36dp) -->
                        <ImageView
                            android:id="@+id/btn_mode"
                            android:layout_width="36dp"
                            android:layout_height="36dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:clickable="true"
                            android:focusable="true" />

                        <!-- 上一首：完全透明无框扁平 (38dp) -->
                        <ImageView
                            android:id="@+id/btn_prev"
                            android:layout_width="38dp"
                            android:layout_height="38dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:layout_marginLeft="14dp"
                            android:layout_marginRight="10dp"
                            android:clickable="true"
                            android:focusable="true" />
                    </LinearLayout>

                    <!-- 底栏中央：突出播放/暂停主按键 (46dp 严格居中) -->
                    <ImageView
                        android:id="@+id/btn_play_pause"
                        android:layout_width="46dp"
                        android:layout_height="46dp"
                        android:background="@drawable/bg_btn_circle_play"
                        android:scaleType="center"
                        android:clickable="true"
                        android:focusable="true" />

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:gravity="left|center_vertical"
                        android:orientation="horizontal">

                        <!-- 下一首：完全透明无框扁平 (38dp) -->
                        <ImageView
                            android:id="@+id/btn_next"
                            android:layout_width="38dp"
                            android:layout_height="38dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:layout_marginLeft="10dp"
                            android:layout_marginRight="14dp"
                            android:clickable="true"
                            android:focusable="true" />

                        <!-- 底部音效图标：完全透明无框扁平推子图标 (36dp，与左侧模式图标完全对称) -->
                        <ImageView
                            android:id="@+id/btn_open_eq"
                            android:layout_width="36dp"
                            android:layout_height="36dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:clickable="true"
                            android:focusable="true" />
                    </LinearLayout>
                </LinearLayout>
            </LinearLayout>

            <LinearLayout
                android:layout_width="126dp"
                android:layout_height="48dp"
                android:orientation="horizontal"
                android:gravity="right|center_vertical">

                <Button
                    android:id="@+id/btn_bottom_fav"
                    android:layout_width="44dp"
                    android:layout_height="44dp"
                    android:background="@drawable/bg_btn_fav"
                    android:text="♡"
                    android:textColor="#ff4081"
                    android:textSize="20sp" />

                <Button
                    android:id="@+id/btn_toggle_queue"
                    android:layout_width="wrap_content"
                    android:layout_height="44dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="播放列表"
                    android:textColor="#e0e0e0"
                    android:textSize="12sp"
                    android:paddingLeft="10dp"
                    android:paddingRight="10dp"
                    android:layout_marginLeft="6dp" />
            </LinearLayout>

        </LinearLayout>

    </LinearLayout>

    <!-- 第二层：独立播放详情页 -->
    <LinearLayout
        android:id="@+id/layout_detail_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="@drawable/bg_main_gradient"
        android:padding="12dp"
        android:clickable="true"
        android:focusable="true"
        android:visibility="gone">

        <!-- 顶栏：下载、音效与关闭按钮 -->
        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="40dp">

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentLeft="true"
                android:layout_centerVertical="true"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <Button
                    android:id="@+id/btn_close_detail"
                    android:layout_width="wrap_content"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="收起详情"
                    android:textColor="#e0e0e0"
                    android:textSize="12sp"
                    android:paddingLeft="8dp"
                    android:paddingRight="8dp" />

                <Button
                    android:id="@+id/btn_detail_download"
                    android:layout_width="wrap_content"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_accent"
                    android:text="⬇ 下载"
                    android:textColor="#00e5ff"
                    android:textSize="12sp"
                    android:paddingLeft="8dp"
                    android:paddingRight="8dp"
                    android:layout_marginLeft="6dp" />

                <Button
                    android:id="@+id/btn_detail_eq"
                    android:layout_width="wrap_content"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_accent"
                    android:text="音效调节"
                    android:textColor="#00e5ff"
                    android:textSize="12sp"
                    android:paddingLeft="8dp"
                    android:paddingRight="8dp"
                    android:layout_marginLeft="6dp" />

                <ImageView
                    android:id="@+id/btn_detail_exit_app"
                    android:layout_width="34dp"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_danger_circle"
                    android:scaleType="center"
                    android:layout_marginLeft="6dp"
                    android:clickable="true"
                    android:focusable="true" />
            </LinearLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_centerInParent="true"
                android:text="正在播放"
                android:textColor="#ffffff"
                android:textSize="16sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentRight="true"
                android:layout_centerVertical="true"
                android:orientation="horizontal">

                <Button
                    android:id="@+id/btn_detail_keep_screen"
                    android:layout_width="wrap_content"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="常亮: 关"
                    android:textColor="#e0e0e0"
                    android:textSize="12sp"
                    android:paddingLeft="8dp"
                    android:paddingRight="8dp" />

                <Button
                    android:id="@+id/btn_detail_queue"
                    android:layout_width="wrap_content"
                    android:layout_height="34dp"
                    android:background="@drawable/bg_btn_default"
                    android:text="播放列表"
                    android:textColor="#e0e0e0"
                    android:textSize="12sp"
                    android:paddingLeft="8dp"
                    android:paddingRight="8dp"
                    android:layout_marginLeft="6dp" />
            </LinearLayout>
        </RelativeLayout>

        <!-- 双栏展示区 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:orientation="horizontal"
            android:layout_marginTop="8dp">

            <!-- 左栏：黑胶与信息 -->
            <LinearLayout
                android:id="@+id/layout_cover_container"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center_horizontal"
                android:background="@drawable/bg_card"
                android:padding="12dp"
                android:clickable="true"
                android:focusable="true">

                <View
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="0.6" />

                <FrameLayout
                    android:id="@+id/layout_vinyl_container"
                    android:layout_width="260dp"
                    android:layout_height="280dp"
                    android:clickable="true"
                    android:focusable="true">

                    <FrameLayout
                        android:id="@+id/fl_vinyl_disc"
                        android:layout_width="240dp"
                        android:layout_height="240dp"
                        android:layout_gravity="bottom|center_horizontal"
                        android:background="@drawable/bg_vinyl">

                        <ImageView
                            android:id="@+id/iv_vinyl_circular_cover"
                            android:layout_width="140dp"
                            android:layout_height="140dp"
                            android:layout_gravity="center"
                            android:scaleType="centerCrop"
                            android:src="@android:drawable/ic_menu_report_image" />
                    </FrameLayout>

                    <com.retro.subsonic.TonearmView
                        android:id="@+id/view_tonearm"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:clickable="true"
                        android:focusable="true" />
                </FrameLayout>

                <ImageView
                    android:id="@+id/iv_square_cover"
                    android:layout_width="240dp"
                    android:layout_height="240dp"
                    android:layout_marginTop="15dp"
                    android:layout_marginBottom="15dp"
                    android:scaleType="centerCrop"
                    android:background="@drawable/bg_card"
                    android:src="@android:drawable/ic_menu_report_image"
                    android:clickable="true"
                    android:focusable="true"
                    android:visibility="gone" />

                <!-- 歌曲标题与红心收藏 -->
                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="4dp">

                    <Button
                        android:id="@+id/btn_detail_fav"
                        android:layout_width="36dp"
                        android:layout_height="36dp"
                        android:background="@drawable/bg_btn_fav"
                        android:text="♡"
                        android:textColor="#ff4081"
                        android:textSize="18sp"
                        android:layout_marginRight="8dp" />

                    <TextView
                        android:id="@+id/tv_detail_title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:maxWidth="200dp"
                        android:text="歌曲名称"
                        android:textColor="#ffffff"
                        android:textSize="18sp"
                        android:textStyle="bold"
                        android:singleLine="true" />
                </LinearLayout>

                <TextView
                    android:id="@+id/tv_detail_artist"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="歌手名称"
                    android:textColor="#a0a5b5"
                    android:textSize="14sp"
                    android:layout_marginTop="2dp"
                    android:singleLine="true" />

                <!-- 码率选择下拉框移动至码率信息显示的左侧 -->
                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="4dp">

                    <Spinner
                        android:id="@+id/spinner_detail_bitrate"
                        android:layout_width="wrap_content"
                        android:layout_height="28dp"
                        android:background="@drawable/bg_btn_pill"
                        android:paddingLeft="8dp"
                        android:paddingRight="8dp"
                        android:layout_marginRight="6dp"
                        android:spinnerMode="dropdown" />

                    <TextView
                        android:id="@+id/tv_detail_quality"
                        android:layout_width="wrap_content"
                        android:layout_height="28dp"
                        android:gravity="center"
                        android:text="标准音质"
                        android:textColor="#00e5ff"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:background="#16181d"
                        android:paddingLeft="10dp"
                        android:paddingRight="10dp" />
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="0.8" />

                <LinearLayout
                    android:id="@+id/layout_detail_seek_box"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="6dp"
                    android:clickable="true">

                    <SeekBar
                        android:id="@+id/detail_seek_bar"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:max="100" />

                    <TextView
                        android:id="@+id/tv_detail_time"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="00:00 / 00:00"
                        android:textColor="#888c99"
                        android:textSize="11sp" />
                </LinearLayout>

                <!-- 详情页控制排：模式切换无背景包裹，切歌无背景包裹，中轴绝对对称 -->
                <LinearLayout
                    android:id="@+id/layout_detail_controls"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="6dp"
                    android:clickable="true">

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:gravity="right|center_vertical"
                        android:orientation="horizontal">

                        <!-- 详情页模式图标：完全透明无框扁平 -->
                        <ImageView
                            android:id="@+id/btn_detail_mode"
                            android:layout_width="38dp"
                            android:layout_height="38dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:clickable="true"
                            android:focusable="true" />

                        <!-- 上一首：完全透明无框扁平 -->
                        <ImageView
                            android:id="@+id/btn_detail_prev"
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:layout_marginLeft="16dp"
                            android:layout_marginRight="14dp"
                            android:clickable="true"
                            android:focusable="true" />
                    </LinearLayout>

                    <!-- 详情页正中心：64dp 青蓝突出圆形播放主键 -->
                    <ImageView
                        android:id="@+id/btn_detail_play_pause"
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:background="@drawable/bg_btn_circle_play"
                        android:scaleType="center"
                        android:clickable="true"
                        android:focusable="true" />

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:gravity="left|center_vertical"
                        android:orientation="horizontal">

                        <!-- 下一首：完全透明无框扁平 -->
                        <ImageView
                            android:id="@+id/btn_detail_next"
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:background="@android:color/transparent"
                            android:scaleType="center"
                            android:layout_marginLeft="14dp"
                            android:layout_marginRight="16dp"
                            android:clickable="true"
                            android:focusable="true" />

                        <!-- 镜像隐形占位：与左侧 38dp 模式图标严格对齐 -->
                        <View
                            android:layout_width="38dp"
                            android:layout_height="38dp"
                            android:visibility="invisible" />
                    </LinearLayout>
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="0.3" />

                <LinearLayout
                    android:id="@+id/layout_detail_bottom_blank"
                    android:layout_width="match_parent"
                    android:layout_height="36dp"
                    android:orientation="vertical"
                    android:gravity="center"
                    android:clickable="true"
                    android:focusable="true">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="[点击空白处切换 歌词 / 播放列表]"
                        android:textColor="#505566"
                        android:textSize="11sp" />
                </LinearLayout>
            </LinearLayout>

            <!-- 右栏：歌词 / 队列 -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1.2"
                android:orientation="vertical"
                android:background="@drawable/bg_card"
                android:padding="12dp"
                android:layout_marginLeft="8dp"
                android:clickable="true"
                android:focusable="true">

                <LinearLayout
                    android:id="@+id/layout_detail_lyrics_view"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:visibility="visible">

                    <RelativeLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="6dp">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_alignParentLeft="true"
                            android:layout_centerVertical="true"
                            android:text="歌词"
                            android:textColor="#a0a5b5"
                            android:textSize="13sp" />

                        <LinearLayout
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_alignParentRight="true"
                            android:layout_centerVertical="true"
                            android:orientation="horizontal">

                            <Button
                                android:id="@+id/btn_lyric_dec"
                                android:layout_width="wrap_content"
                                android:layout_height="30dp"
                                android:background="@drawable/bg_btn_default"
                                android:text="缩小 -"
                                android:textColor="#e0e0e0"
                                android:textSize="11sp"
                                android:paddingLeft="8dp"
                                android:paddingRight="8dp" />

                            <Button
                                android:id="@+id/btn_lyric_inc"
                                android:layout_width="wrap_content"
                                android:layout_height="30dp"
                                android:background="@drawable/bg_btn_default"
                                android:text="增大 +"
                                android:textColor="#e0e0e0"
                                android:textSize="11sp"
                                android:paddingLeft="8dp"
                                android:paddingRight="8dp"
                                android:layout_marginLeft="6dp" />
                        </LinearLayout>
                    </RelativeLayout>

                    <ScrollView
                        android:id="@+id/scroll_lyrics"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:fillViewport="true"
                        android:scrollbars="none">

                        <LinearLayout
                            android:id="@+id/layout_lyrics_container"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:gravity="center_horizontal"
                            android:paddingTop="150dp"
                            android:paddingBottom="150dp" />
                    </ScrollView>
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/layout_detail_queue_view"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="当前播放列表"
                        android:textColor="#a0a5b5"
                        android:textSize="13sp"
                        android:layout_marginBottom="6dp" />

                    <ListView
                        android:id="@+id/lv_detail_queue"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:divider="#333842"
                        android:dividerHeight="1dp" />
                </LinearLayout>

            </LinearLayout>

        </LinearLayout>

    </LinearLayout>

</FrameLayout>
