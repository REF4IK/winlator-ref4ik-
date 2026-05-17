package com.winlator.cmod;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.R;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.SaveEditDialog;
import com.winlator.cmod.contentdialog.SaveSettingsDialog;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.saves.Save;
import com.winlator.cmod.saves.SaveManager;
import com.winlator.cmod.steam.SteamLibraryActivity;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static String PACKAGE_NAME;
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences sharedPreferences;

    // Add SaveSettingsDialog and SaveEditDialog instances
    private SaveSettingsDialog saveSettingsDialog;
    private SaveEditDialog saveEditDialog;
    private SaveManager saveManager;
    private ContainerManager containerManager;

    private SaveEditDialog currentSaveEditDialog;

    private boolean isDarkMode;

//    private void cleanupErroneousContainer() {
//        // Define the specific path to the erroneous directory
//        File erroneousDir = new File(Environment.getExternalStorageDirectory(), "Android/data/com.winlator/files/Backups");
//
//        // Log the contents of the directory
//        logSpecificDirectoryContents(erroneousDir);
//
//        // Check if the directory exists and delete it if found
//        if (erroneousDir.exists() && erroneousDir.isDirectory()) {
//            if (FileUtils.delete(erroneousDir)) {
//                Log.i("MainActivity", "Successfully deleted erroneous container directory: " + erroneousDir.getPath());
//                Toast.makeText(this, "Erroneous container directory deleted.", Toast.LENGTH_SHORT).show();
//            } else {
//                Log.e("MainActivity", "Failed to delete erroneous container directory: " + erroneousDir.getPath());
//                Toast.makeText(this, "Failed to delete erroneous container directory.", Toast.LENGTH_SHORT).show();
//            }
//        } else {
//            Log.i("MainActivity", "Erroneous container directory not found: " + erroneousDir.getPath());
//            Toast.makeText(this, "Erroneous container directory not found.", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    // Method to log the contents of a specific directory
//    private void logSpecificDirectoryContents(File directory) {
//        if (directory == null || !directory.isDirectory()) {
//            Log.e("MainActivity", "Provided path is not a directory: " + directory);
//            return;
//        }
//
//        Log.d("MainActivity", "Contents of directory: " + directory.getAbsolutePath());
//        File[] files = directory.listFiles();
//        if (files != null) {
//            for (File file : files) {
//                Log.d("MainActivity", (file.isDirectory() ? "Directory: " : "File: ") + file.getName());
//            }
//        } else {
//            Log.d("MainActivity", "No files found in directory: " + directory.getAbsolutePath());
//        }
//    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize package name
        if (PACKAGE_NAME == null) {
            PACKAGE_NAME = getPackageName();
        }

//        cleanupErroneousContainer();

        // Get shared preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if Big Picture Mode is enabled
        boolean isBigPictureModeEnabled = sharedPreferences.getBoolean("enable_big_picture_mode", false);

        if (isBigPictureModeEnabled) {
            // If enabled, launch the BigPictureActivity and finish MainActivity
            Intent intent = new Intent(MainActivity.this, BigPictureActivity.class);
            startActivity(intent);
        }

        // Load the user's preferred theme
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false);

        // Apply the theme based on the preference
        if (isDarkMode) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        // Apply saved language preference
        String savedLanguage = sharedPreferences.getString("app_language", "system");
        applyLanguage(savedLanguage);


        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        NavigationView navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);

        setSupportActionBar(findViewById(R.id.Toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
        }

        // Determine text color based on dark mode
        int textColor = isDarkMode ? Color.WHITE : Color.BLACK;
        setNavigationViewItemTextColor(navigationView, textColor);
        

        // Initialize SaveManager and ContainerManager
        saveManager = new SaveManager(this);
        containerManager = new ContainerManager(this);

        Intent intent = getIntent();
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.main_menu_input_controls));
            navigationView.setCheckedItem(R.id.main_menu_input_controls);
        } else {
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : R.id.main_menu_containers;

            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);

            if (!requestAppPermissions()) {
                ImageFsInstaller.installIfNeeded(this);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                showAllFilesAccessDialog();
            }
            
            // Check for updates on startup
            checkForUpdatesOnStartup();
        }
    }

    private void showAllFilesAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.all_files_access_required)
                .setMessage(R.string.all_files_access_message)
                .setPositiveButton(R.string.okay, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ImageFsInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("WinActivity", "onActivityResult called with requestCode: " + requestCode + " and resultCode: " + resultCode);

        if (saveSettingsDialog != null && saveSettingsDialog.isShowing()) {
            Log.d("WinActivity", "Forwarding result to SaveSettingsDialog");
            saveSettingsDialog.onActivityResult(requestCode, resultCode, data);
        } else if (saveEditDialog != null && saveEditDialog.isShowing()) {
            Log.d("WinActivity", "Forwarding result to SaveEditDialog");
            saveEditDialog.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.d("WinActivity", "No dialog found for request code: " + requestCode);
        }

        // Handle file picker for wallpaper selection
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && openFileCallback != null) {
                openFileCallback.call(uri);
                openFileCallback = null; // Reset callback after use
            }
        }

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String filePath = data.getStringExtra("selectedFilePath");
            if (filePath != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Выбран файл")
                        .setMessage(filePath)
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void showSavesFragment() {
        SavesFragment fragment = new SavesFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.FLFragmentContainer, fragment)
                .commit();
    }

    // Method to show SaveEditDialog
    public void showSaveEditDialog(Save saveToEdit) {
        saveEditDialog = new SaveEditDialog(this, saveManager, containerManager, saveToEdit);

        // Check for dark mode and set the background accordingly
        if (isDarkMode) {
            saveEditDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        } else {
            saveEditDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
        }

        saveEditDialog.show();
    }

    public void onSaveAdded() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.FLFragmentContainer);
        if (currentFragment instanceof SavesFragment) {
            ((SavesFragment) currentFragment).refreshSavesList();
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragments = fragmentManager.getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof ContainersFragment && fragment.isVisible()) {
                finish();
                return;
            }
        }

        show(new ContainersFragment(), true);  // Pass `true` to trigger the reverse animation
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    private boolean requestAppPermissions() {
        boolean hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasManageStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();

        if (hasWritePermission && hasReadPermission && hasManageStoragePermission) {
            return false; // All permissions are granted
        }

        if (!hasWritePermission || !hasReadPermission) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        }

        return true; // Permissions are still being requested
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            // Toggle the drawer
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START, false);
            } else {
                drawerLayout.openDrawer(GravityCompat.START, false);
            }
            return true;
        } else if (menuItem.getItemId() == R.id.saves_menu_add) {
            // Check if we are editing a save
            Intent intent = getIntent();
            int editSaveId = intent.getIntExtra("edit_save_id", -1);
            Save saveToEdit = editSaveId >= 0 ? saveManager.getSaveById(editSaveId) : null;

            // Create and show SaveEditDialog or SaveSettingsDialog as appropriate
            if (saveToEdit != null) {
                // Ensure previous dialog is dismissed before showing a new one
                if (saveEditDialog != null && saveEditDialog.isShowing()) {
                    saveEditDialog.dismiss();
                }
                showSaveEditDialog(saveToEdit); // Use the correct method to show SaveEditDialog
            } else {
                saveSettingsDialog = new SaveSettingsDialog(this, saveManager, containerManager);

                // Check for dark mode and set the background accordingly
                if (isDarkMode) {
                    saveSettingsDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
                } else {
                    saveSettingsDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
                }

                saveSettingsDialog.show();
            }
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    public void toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START, false);
        } else {
            drawerLayout.openDrawer(GravityCompat.START, false);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        switch (item.getItemId()) {
            case R.id.main_menu_shortcuts:
                show(new ShortcutsFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_containers:
                show(new ContainersFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_input_controls:
                show(new InputControlsFragment(selectedProfileId), false);  // Forward animation
                break;
            case R.id.main_menu_box_rc:
                show(new Box86_64RCFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_contents:
                show(new ContentsFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_installed_components:
                show(new InstalledComponentsFragment(), false);
                break;
            case R.id.main_menu_steam:
                drawerLayout.closeDrawer(GravityCompat.START, false);
                startActivity(new Intent(this, SteamLibraryActivity.class));
                break;
            case R.id.main_menu_adrenotools_gpu_drivers:
                show(new AdrenotoolsFragment(), false);
                break;
            case R.id.main_menu_saves:
                show(new SavesFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_gamepad_test:
                drawerLayout.closeDrawer(GravityCompat.START, false);
                Intent gamepadIntent = new Intent(this, GamePadTestActivity.class);
                startActivity(gamepadIntent);
                break;
            case R.id.main_menu_settings:
                show(new SettingsFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_about:
                drawerLayout.closeDrawer(GravityCompat.START, false);
                showAboutDialog();
                break;
            case R.id.main_menu_file_manager:
                drawerLayout.closeDrawer(GravityCompat.START, false);
                Intent fmIntent = new Intent(this, FileManagerActivity.class);
                startActivityForResult(fmIntent, 1001);
                break;
        }
        return true;
    }


//    private void show(Fragment fragment) {
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        fragmentManager.beginTransaction()
//                .replace(R.id.FLFragmentContainer, fragment)
//                .commit();
//
//        drawerLayout.closeDrawer(GravityCompat.START);
//    }

    private void show(Fragment fragment, boolean reverse) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        String animationType = sharedPreferences.getString("transition_animation", "none");
        boolean fastMode = "none".equals(animationType);

        if (fastMode) {
            drawerLayout.closeDrawer(GravityCompat.START, false);
            findViewById(R.id.FLFragmentContainer).post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    fragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.FLFragmentContainer, fragment)
                            .commitNowAllowingStateLoss();
                }
            });
            return;
        }

        int enterAnim, exitAnim;

        switch (animationType) {
            case "slide_horizontal":
                if (reverse) {
                    enterAnim = R.anim.slide_in_left;
                    exitAnim = R.anim.slide_out_right;
                } else {
                    enterAnim = R.anim.slide_in_right;
                    exitAnim = R.anim.slide_out_left;
                }
                break;
            case "fade":
                enterAnim = R.anim.fade_in;
                exitAnim = R.anim.fade_out;
                break;
            case "zoom":
                enterAnim = R.anim.zoom_in;
                exitAnim = R.anim.zoom_out;
                break;
            case "slide_vertical":
            default:
                if (reverse) {
                    enterAnim = R.anim.slide_in_down;
                    exitAnim = R.anim.slide_out_up;
                } else {
                    enterAnim = R.anim.slide_in_up;
                    exitAnim = R.anim.slide_out_down;
                }
                break;
        }

        fragmentManager.beginTransaction()
                .setCustomAnimations(enterAnim, exitAnim)
                .setReorderingAllowed(true)
                .replace(R.id.FLFragmentContainer, fragment)
                .commit();

        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void showAboutDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.about_dialog);
        dialog.findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        if (isDarkMode) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
        }

        try {
            final PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

            TextView tvWebpage = dialog.findViewById(R.id.TVWebpage);
            tvWebpage.setText(Html.fromHtml("<a href=\"https://www.winlator.org\">winlator.org</a>", Html.FROM_HTML_MODE_LEGACY));
            tvWebpage.setMovementMethod(LinkMovementMethod.getInstance());

            ((TextView) dialog.findViewById(R.id.TVAppVersion)).setText(getString(R.string.version) + " " + pInfo.versionName);

            String creditsAndThirdPartyAppsHTML = String.join("<br />",
                    "<b>Winlator Bionic REF4IK MOD by ref4ik</b>",
                    "Telegram: <a href=\"https://t.me/winlatorruu\">t.me/winlatorruu</a>",
                    "GitHub: <a href=\"https://github.com/REF4IK/winlator-ref4ik-\">github.com/REF4IK/winlator-ref4ik-</a>",
                    "---",
                    "BrunoSX Creator Winlator(<a href=\"https://github.com/brunodev85\">github.com/brunodev85</a>)",
                    "---",
                    "Coffincolors  Winlator cmod (<a href=\"https://github.com/coffincolors/winlator\">Fork</a>)",
                    "Pipetto-crypto WInlator bionic(<a href=\"https://github.com/Pipetto-crypto/winlator/tree/winlator_bionic\">winlator_bionic</a>)",
                    "---",
                    "Big Picture Mode Music by",
                    "Dale Melvin Blevens III (Fumer)",
                    "---",
                    "Components:",
                    "Ubuntu RootFs (<a href=\"https://releases.ubuntu.com/focal\">Focal Fossa</a>)",
                    "Wine (<a href=\"https://www.winehq.org\">winehq.org</a>)",
                    "Box86/Box64 by <a href=\"https://github.com/ptitSeb\">ptitseb</a>",
                    "FEX-Emu (<a href=\"https://github.com/FEX-Emu/FEX\">github.com/FEX-Emu/FEX</a>)",
                    "PRoot (<a href=\"https://proot-me.github.io\">proot-me.github.io</a>)",
                    "Mesa (Turnip/Zink/VirGL) (<a href=\"https://www.mesa3d.org\">mesa3d.org</a>)",
                    "DXVK (<a href=\"https://github.com/doitsujin/dxvk\">github.com/doitsujin/dxvk</a>)",
                    "VKD3D (<a href=\"https://gitlab.winehq.org/wine/vkd3d\">gitlab.winehq.org/wine/vkd3d</a>)",
                    "D8VK (<a href=\"https://github.com/AlpyneDreams/d8vk\">github.com/AlpyneDreams/d8vk</a>)",
                    "linux-fg (<a href=\"https://github.com/xXJSONDeruloXx/linux-fg\">github.com/xXJSONDeruloXx/linux-fg</a>)",
                    "CNC DDraw (<a href=\"https://github.com/FunkyFr3sh/cnc-ddraw\">github.com/FunkyFr3sh/cnc-ddraw</a>)",
                    "WinlatorWCPHub by Arihany (<a href=\"https://github.com/Arihany/WinlatorWCPHub\">github.com/Arihany/WinlatorWCPHub</a>)",
                    "---",
                    "GPU Drivers:",
                    "K11MCH1 AdrenoTools Drivers (<a href=\"https://github.com/K11MCH1/AdrenoToolsDrivers\">github.com/K11MCH1/AdrenoToolsDrivers</a>)",
                    "MrPurple666 Purple Turnip (<a href=\"https://github.com/MrPurple666/purple-turnip\">github.com/MrPurple666/purple-turnip</a>)",
                    "Weab-chan Freedreno Turnip CI (<a href=\"https://github.com/Weab-chan/freedreno_turnip-CI\">github.com/Weab-chan/freedreno_turnip-CI</a>)",
                    "crueter GameHub 8Elite Drivers (<a href=\"https://github.com/crueter/GameHub-8Elite-Drivers\">github.com/crueter/GameHub-8Elite-Drivers</a>)"
            );

            TextView tvCreditsAndThirdPartyApps = dialog.findViewById(R.id.TVCreditsAndThirdPartyApps);
            tvCreditsAndThirdPartyApps.setText(Html.fromHtml(creditsAndThirdPartyAppsHTML, Html.FROM_HTML_MODE_LEGACY));
            tvCreditsAndThirdPartyApps.setMovementMethod(LinkMovementMethod.getInstance());

            String glibcExpVersionForkHTML = String.join("<br />",
                    "longjunyu2's <a href=\"https://github.com/longjunyu2/winlator/tree/use-glibc-instead-of-proot\">(Fork)</a>");
            TextView tvGlibcExpVersionFork = dialog.findViewById(R.id.TVGlibcExpVersionFork);
            tvGlibcExpVersionFork.setText(Html.fromHtml(glibcExpVersionForkHTML, Html.FROM_HTML_MODE_LEGACY));
            tvGlibcExpVersionFork.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        dialog.show();
    }

    private void setNavigationViewItemTextColor(NavigationView navigationView, int color) {
        for (int i = 0; i < navigationView.getMenu().size(); i++) {
            MenuItem menuItem = navigationView.getMenu().getItem(i);
            setMenuItemTextColor(menuItem, color);

            // If the menu item has sub-items, iterate through them
            if (menuItem.hasSubMenu()) {
                for (int j = 0; j < menuItem.getSubMenu().size(); j++) {
                    MenuItem subMenuItem = menuItem.getSubMenu().getItem(j);
                    setMenuItemTextColor(subMenuItem, color);
                }
            }
        }
    }

    private void setMenuItemTextColor(MenuItem menuItem, int color) {
        SpannableString spanString = new SpannableString(menuItem.getTitle());
        spanString.setSpan(new ForegroundColorSpan(color), 0, spanString.length(), 0);
        menuItem.setTitle(spanString);
    }

    private void applyLanguage(String languageCode) {
        if ("system".equals(languageCode)) {
            // Reset to system default
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList());
        } else if ("en".equals(languageCode)) {
            // Set to English
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("en");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        } else if ("ru".equals(languageCode)) {
            // Set to Russian
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("ru");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        }
    }

    private void checkForUpdatesOnStartup() {
        if (!sharedPreferences.getBoolean("receive_beta_updates", false)) {
            Log.d("MainActivity", "Startup update check skipped because update notifications are disabled");
            return;
        }

        com.winlator.cmod.update.UpdateManager updateManager = new com.winlator.cmod.update.UpdateManager(this);
        
        updateManager.checkForUpdates(new com.winlator.cmod.update.UpdateManager.UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(com.winlator.cmod.update.GitHubRelease release) {
                runOnUiThread(() -> {
                    com.winlator.cmod.contentdialog.UpdateDialog updateDialog = 
                        new com.winlator.cmod.contentdialog.UpdateDialog(MainActivity.this, release);
                    updateDialog.show();
                });
            }
            
            @Override
            public void onNoUpdateAvailable() {
                // Silently do nothing on startup if no update
            }
            
            @Override
            public void onError(String error) {
                // Silently fail on startup - don't bother user
            }
        });
    }
}
