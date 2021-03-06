package com.zane.smapiinstaller;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;
import com.hjq.language.LanguagesManager;
import com.lmntrx.android.library.livin.missme.ProgressDialog;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.model.Response;
import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.crashes.Crashes;
import com.zane.smapiinstaller.constant.AppConfigKey;
import com.zane.smapiinstaller.constant.Constants;
import com.zane.smapiinstaller.constant.DialogAction;
import com.zane.smapiinstaller.dto.AppUpdateCheckResultDto;
import com.zane.smapiinstaller.entity.AppConfig;
import com.zane.smapiinstaller.entity.AppConfigDao;
import com.zane.smapiinstaller.entity.DaoSession;
import com.zane.smapiinstaller.entity.FrameworkConfig;
import com.zane.smapiinstaller.logic.CommonLogic;
import com.zane.smapiinstaller.logic.ConfigManager;
import com.zane.smapiinstaller.logic.GameLauncher;
import com.zane.smapiinstaller.logic.ModAssetsManager;
import com.zane.smapiinstaller.utils.DialogUtils;
import com.zane.smapiinstaller.utils.JSONUtil;
import com.zane.smapiinstaller.utils.JsonCallback;
import com.zane.smapiinstaller.utils.TranslateUtil;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author Zane
 */
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.drawer_layout)
    DrawerLayout drawer;

    @BindView(R.id.nav_view)
    NavigationView navigationView;

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        } else {
            initView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initView();
        } else {
            this.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        AppCenter.start(getApplication(), Constants.APP_CENTER_SECRET, Analytics.class, Crashes.class);
        requestPermissions();
    }

    private void initView() {
        setSupportActionBar(toolbar);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_install, R.id.nav_config, R.id.nav_help, R.id.nav_download, R.id.nav_about)
                .setOpenableLayout(drawer)
                .build();
        final NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        checkAppUpdate();
    }

    private void checkAppUpdate() {
        DaoSession daoSession = ((MainApplication) this.getApplication()).getDaoSession();
        AppConfigDao appConfigDao = daoSession.getAppConfigDao();
        AppConfig appConfig = appConfigDao.queryBuilder().where(AppConfigDao.Properties.Name.eq(AppConfigKey.IGNORE_UPDATE_VERSION_CODE)).build().unique();
        OkGo.<AppUpdateCheckResultDto>get(Constants.SELF_UPDATE_CHECK_SERVICE_URL).execute(new JsonCallback<AppUpdateCheckResultDto>(AppUpdateCheckResultDto.class) {
            @Override
            public void onSuccess(Response<AppUpdateCheckResultDto> response) {
                AppUpdateCheckResultDto dto = response.body();
                if (dto != null && CommonLogic.getVersionCode(MainActivity.this) < dto.getVersionCode()) {
                    if (appConfig != null && StringUtils.equals(appConfig.getValue(), String.valueOf(dto.getVersionCode()))) {
                        return;
                    }
                    DialogUtils.showConfirmDialog(toolbar, R.string.settings_check_for_updates,
                            MainActivity.this.getString(R.string.app_update_detected, dto.getVersionName()), (dialog, which) -> {
                                if (which == DialogAction.POSITIVE) {
                                    CommonLogic.openInPlayStore(MainActivity.this);
                                } else {
                                    AppConfig config;
                                    if (appConfig != null) {
                                        config = appConfig;
                                        config.setValue(String.valueOf(dto.getVersionCode()));
                                    } else {
                                        config = new AppConfig(null, AppConfigKey.IGNORE_UPDATE_VERSION_CODE, String.valueOf(dto.getVersionCode()));
                                    }
                                    appConfigDao.insertOrReplace(config);
                                }
                            });
                }
            }
        });
    }

    @OnClick(R.id.launch)
    void launchButtonClick() {
        new GameLauncher(navigationView).launch();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ConfigManager manager = new ConfigManager();
        FrameworkConfig config = manager.getConfig();
        menu.findItem(R.id.settings_verbose_logging).setChecked(config.isVerboseLogging());
        menu.findItem(R.id.settings_check_for_updates).setChecked(config.isCheckForUpdates());
        menu.findItem(R.id.settings_developer_mode).setChecked(config.isDeveloperMode());
        Constants.MOD_PATH = config.getModsPath();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.isCheckable()) {
            if (item.isChecked()) {
                item.setChecked(false);
            } else {
                item.setChecked(true);
            }
        }
        ConfigManager manager = new ConfigManager();
        FrameworkConfig config = manager.getConfig();
        switch (item.getItemId()) {
            case R.id.settings_verbose_logging:
                config.setVerboseLogging(item.isChecked());
                break;
            case R.id.settings_check_for_updates:
                config.setCheckForUpdates(item.isChecked());
                break;
            case R.id.settings_developer_mode:
                config.setDeveloperMode(item.isChecked());
                break;
            case R.id.settings_set_mod_path:
                DialogUtils.showInputDialog(toolbar, R.string.input, R.string.input_mods_path, Constants.MOD_PATH, Constants.MOD_PATH, (dialog, input) -> {
                    if (StringUtils.isNoneBlank(input)) {
                        String pathString = input.toString();
                        File file = new File(Environment.getExternalStorageDirectory(), pathString);
                        if (file.exists() && file.isDirectory()) {
                            Constants.MOD_PATH = pathString;
                            config.setModsPath(pathString);
                            manager.flushConfig();
                        } else {
                            DialogUtils.showAlertDialog(drawer, R.string.error, R.string.error_illegal_path);
                        }
                    }
                });
                return true;
            case R.id.settings_language:
                selectLanguageLogic();
                return true;
            case R.id.settings_translation_service:
                selectTranslateServiceLogic();
                return true;
            case R.id.toolbar_update_check:
                checkModUpdateLogic();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        manager.flushConfig();
        return true;
    }

    private int getTranslateServiceIndex(AppConfig selectedTranslator) {
        if (selectedTranslator == null) {
            return 0;
        }
        switch (selectedTranslator.getValue()) {
            case "OFF":
                return 0;
            case "Google":
                return 1;
            default:
                return 2;
        }
    }

    private void selectTranslateServiceLogic() {
        DaoSession daoSession = ((MainApplication) this.getApplication()).getDaoSession();
        AppConfigDao appConfigDao = daoSession.getAppConfigDao();
        int index = getTranslateServiceIndex(appConfigDao.queryBuilder().where(AppConfigDao.Properties.Name.eq(AppConfigKey.ACTIVE_TRANSLATOR)).build().unique());
        DialogUtils.showSingleChoiceDialog(toolbar, R.string.settings_translation_service, R.array.translators, index, (dialog, position) -> {
            AppConfig activeTranslator = appConfigDao.queryBuilder().where(AppConfigDao.Properties.Name.eq(AppConfigKey.ACTIVE_TRANSLATOR)).build().unique();
            switch (position) {
                case 0:
                    if (activeTranslator != null) {
                        appConfigDao.delete(activeTranslator);
                    }
                    break;
                case 1:
                    if (activeTranslator == null) {
                        activeTranslator = new AppConfig(null, AppConfigKey.ACTIVE_TRANSLATOR, TranslateUtil.GOOGLE);
                    } else {
                        activeTranslator.setValue(TranslateUtil.GOOGLE);
                    }
                    appConfigDao.insertOrReplace(activeTranslator);
                    break;
                case 2:
                    if (activeTranslator == null) {
                        activeTranslator = new AppConfig(null, AppConfigKey.ACTIVE_TRANSLATOR, TranslateUtil.YOU_DAO);
                    } else {
                        activeTranslator.setValue(TranslateUtil.YOU_DAO);
                    }
                    appConfigDao.insertOrReplace(activeTranslator);
                    break;
                default:
            }
        });
    }

    private void selectLanguageLogic() {
        DialogUtils.showListItemsDialog(toolbar, R.string.settings_set_language, R.array.languages, (dialog, position) -> {
            boolean restart;
            switch (position) {
                case 0:
                    restart = LanguagesManager.setSystemLanguage(this);
                    break;
                case 1:
                    restart = LanguagesManager.setAppLanguage(this, Locale.ENGLISH);
                    break;
                case 2:
                    restart = LanguagesManager.setAppLanguage(this, Locale.SIMPLIFIED_CHINESE);
                    break;
                case 3:
                    restart = LanguagesManager.setAppLanguage(this, Locale.TRADITIONAL_CHINESE);
                    break;
                case 4:
                    restart = LanguagesManager.setAppLanguage(this, Locale.KOREA);
                    break;
                case 5:
                    restart = LanguagesManager.setAppLanguage(this, new Locale("th", ""));
                    break;
                case 6:
                    restart = LanguagesManager.setAppLanguage(this, new Locale("es", ""));
                    break;
                case 7:
                    restart = LanguagesManager.setAppLanguage(this, Locale.FRENCH);
                    break;
                case 8:
                    restart = LanguagesManager.setAppLanguage(this, new Locale("pt", ""));
                    break;
                case 9:
                    restart = LanguagesManager.setAppLanguage(this, new Locale("in", ""));
                    break;
                default:
                    return;
            }
            if (restart) {
                // 我们可以充分运用 Activity 跳转动画，在跳转的时候设置一个渐变的效果
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(R.anim.fragment_fade_enter, R.anim.fragment_fade_exit);
                finish();
            }
        });
    }

    private void checkModUpdateLogic() {
        ModAssetsManager modAssetsManager = new ModAssetsManager(toolbar);
        modAssetsManager.checkModUpdate((list) -> {
            if (list.isEmpty()) {
                CommonLogic.runOnUiThread(this, (activity) -> Toast.makeText(activity, R.string.no_update_text, Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                NavController controller = Navigation.findNavController(this, R.id.nav_host_fragment);
                controller.navigate(MobileNavigationDirections.actionNavAnyToModUpdateFragment(JSONUtil.toJson(list)));
            } catch (Exception e) {
                Crashes.trackError(e);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        Object dialog = DialogUtils.getCurrentDialog();
        if (dialog instanceof ProgressDialog) {
            ProgressDialog progressDialog = ((ProgressDialog) dialog);
            progressDialog.onBackPressed(
                    () -> {
                        super.onBackPressed();
                        return null;
                    }
            );
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // 国际化适配（绑定语种）
        super.attachBaseContext(LanguagesManager.attach(newBase));
    }

    @Override
    protected void onDestroy() {
        DialogUtils.dismissDialog();
        super.onDestroy();
    }
}

