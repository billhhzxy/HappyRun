package com.example.happyrunning.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;

import com.blankj.utilcode.util.SPUtils;
import com.example.happyrunning.MyApplication;
import com.example.happyrunning.R;
import com.example.happyrunning.commons.utils.Status_sp;
import com.example.happyrunning.commons.utils.UIhelper;
import com.example.happyrunning.commons.utils.Utils;
import com.example.happyrunning.ui.BaseActivity;
import com.example.happyrunning.ui.permission.PermissionHelper;
import com.example.happyrunning.ui.permission.PermissionListener;
import com.gyf.barlibrary.ImmersionBar;

import butterknife.BindView;
import butterknife.ButterKnife;

public class Splash extends BaseActivity {
    @BindView(R.id.img_url)
    ImageView imgUrl;

    @BindView(R.id.versions)
    TextView versions;

    /** 防误退出：上次按返回键时间 */
    private long lastBackPressed;
    private static final int QUIT_INTERVAL = 3000;

    /** 需要申请的权限 */
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /** Handler 延迟跳转 */
    private final Handler handler = new Handler();

    @Override
    protected void initImmersionBar() {
        super.initImmersionBar();
        if (ImmersionBar.hasNavigationBar(this)) {
            ImmersionBar.with(this)
                    .transparentNavigationBar()
                    .init();
        }
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_splash;
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        ButterKnife.bind(this);

        // 设置背景和版本号
        imgUrl.setImageResource(R.mipmap.splash_bg);
        versions.setText(UIhelper.getString(
                R.string.splash_appversionname,
                MyApplication.getAppVersionName()
        ));

        // 延迟 2 秒后执行权限申请 & 跳转逻辑
        handler.postDelayed(this::checkPermissionsAndProceed, 2000);
    }

    @Override
    public void initListener() {
        // no-op
    }

    /** 申请必要权限，或直接跳到下一页 */
    private void checkPermissionsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionHelper.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    getString(R.string.app_name) + " 需要存储和定位权限",
                    new PermissionListener() {
                        @Override
                        public void onPassed() {
                            goNext();
                        }
                    }
            );
        } else {
            goNext();
        }
    }

    /** 根据是否已登录，跳转到主页或登录页 */
    private void goNext() {
        boolean isLogin = SPUtils.getInstance().getBoolean(Status_sp.ISLOGIN, false);
        Intent it = new Intent(
                this,
                isLogin ? MainActivity.class : Login.class
        );
        startActivity(it);
        finish();
    }

    /** 双击返回键退出 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            if (now - lastBackPressed > QUIT_INTERVAL) {
                lastBackPressed = now;
                Utils.showToast(this, "再按一次退出");
            } else {
                handler.removeCallbacksAndMessages(null);
                MyApplication.closeApp(this);
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
