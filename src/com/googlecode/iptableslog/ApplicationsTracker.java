package com.googlecode.iptableslog;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.app.ProgressDialog;
import android.os.Handler;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class ApplicationsTracker {
  public static ArrayList<AppEntry> installedApps;
  public static HashMap<String, AppEntry> installedAppsHash;
  public static HashMap<String, Object> loadingIcon = new HashMap<String, Object>();
  public static HashMap<String, Object> loadingLabel = new HashMap<String, Object>();
  public static ProgressDialog dialog;
  public static int appCount;
  public static Object dialogLock = new Object();
  public static Object installedAppsLock = new Object();

  public static class AppEntry {
    boolean labelLoaded;
    String name;
    String nameLowerCase;
    String packageName;
    Drawable icon;
    int uid;
    String uidString;

    public String toString() {
      return "(" + uidString + ") " + name;
    }
  }

  public static void restoreData(IptablesLogData data) {
    synchronized(installedAppsLock) {
      installedApps = data.applicationsTrackerInstalledApps;
    }

    installedAppsHash = data.applicationsTrackerInstalledAppsHash;
  }

  public static String loadLabel(final Context context, final String packageName, final Object ref) {
    AppEntry item = null;

    for(AppEntry app : installedApps) {
      if(app.packageName.equals(packageName)) {
        item = app;
        break;
      }
    }

    if(item == null) {
      MyLog.d("Failed to find item for " + packageName);
      return packageName;
    }

    if(item.labelLoaded) {
      return item.name;
    }

    Object loading;
    synchronized(loadingLabel) {
      loading = loadingLabel.get(packageName);
    }

    if(loading == null) {
      synchronized(loadingLabel) {
        loadingLabel.put(packageName, packageName);
      }

      final AppEntry entry = item;
      new Thread(new Runnable() {
        public void run() {
          MyLog.d("Loading label for " + entry);
          PackageManager pm = context.getPackageManager();
          List<ApplicationInfo> apps = pm.getInstalledApplications(0);

          for(final ApplicationInfo app : apps) {
            if(app.packageName.equals(packageName)) {
              entry.name = context.getPackageManager().getApplicationLabel(app).toString();
              entry.nameLowerCase = entry.name.toLowerCase();
              entry.labelLoaded = true;
              
              synchronized(loadingLabel) {
                loadingLabel.remove(packageName);
              }

              if(ref instanceof LogView.ListItem) {
                ((LogView.ListItem)ref).mLabelLoaded = true;
              } else if(ref instanceof AppView.GroupItem) {
                ((AppView.GroupItem)ref).app.labelLoaded = true;
              }

              IptablesLog.handler.post(new Runnable() {
                public void run() {
                  IptablesLog.logView.refreshAdapter();
                  IptablesLog.appView.refreshAdapter();
                }
              });
            }
          }
        }
      }, "LoadLabel:" + packageName).start();

      return packageName;
    }

    return packageName;
  }

  public static Drawable loadIcon(final Context context, final String packageName) {
    AppEntry item = null;

    for(AppEntry app : installedApps) {
      if(app.packageName.equals(packageName)) {
        item = app;
        break;
      }
    }

    if(item == null) {
      MyLog.d("Failed to find item for " + packageName);
      return null;
    }

    if(item.icon != null) {
      return item.icon;
    }

    Object loading;
    synchronized(loadingIcon) {
      loading = loadingIcon.get(packageName);
    }

    if(loading == null) {
      synchronized(loadingIcon) {
        loadingIcon.put(packageName, packageName);
      }

      final AppEntry entry = item;
      new Thread(new Runnable() {
        public void run() {
          MyLog.d("Loading icon for " + entry);
          try {
            entry.icon = context.getPackageManager().getApplicationIcon(packageName);

            synchronized(loadingIcon) {
              loadingIcon.remove(packageName);
            }

            IptablesLog.handler.post(new Runnable() {
              public void run() {
                IptablesLog.logView.refreshAdapter();
                IptablesLog.appView.refreshAdapter();
              }
            });
          } catch(Exception e) {
          }
        }
      }, "LoadIcon:" + packageName).start();

      return null;
    }

    return null;
  }

  public static void getInstalledApps(final Context context, final Handler handler) {
    MyLog.d("Loading installed apps");

    synchronized(installedAppsLock) {
      if(IptablesLog.data == null) {
        installedApps = new ArrayList<AppEntry>();
        installedAppsHash = new HashMap<String, AppEntry>();
      } else {
        restoreData(IptablesLog.data);
        installedApps.clear();
        installedAppsHash.clear();
      }

      PackageManager pm = context.getPackageManager();
      List<ApplicationInfo> apps = pm.getInstalledApplications(0);
      appCount = apps.size();

      handler.post(new Runnable() {
        public void run() {
          MyLog.d("Showing progress dialog; size: " + appCount);

          synchronized(dialogLock) {
            dialog = new ProgressDialog(context);
            dialog.setIndeterminate(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(appCount);
            dialog.setCancelable(false);
            dialog.setTitle("");
            dialog.setMessage("Loading apps");
            dialog.show();
          }
        }
      });

      int count = 0;

      for(final ApplicationInfo app : apps) {
        MyLog.d("Processing app " + app);

        if(IptablesLog.initRunner.running == false) {
          MyLog.d("[ApplicationsTracker] Initialization aborted");
          return;
        }

        final int progress = ++count;
        handler.post(new Runnable() {
          public void run() {
            synchronized(dialogLock) {
              if(dialog != null) {
                MyLog.d("Updating dialog progress: " + progress + " " + app.processName);
                dialog.setProgress(progress);
              }
            }
          }
        });

        int uid = app.uid;
        String sUid = Integer.toString(uid);

        AppEntry entryHash = installedAppsHash.get(sUid);

        AppEntry entry = new AppEntry();
        
        if(app.name != null) {
          MyLog.d("Set name [" + app.name + "]");
          entry.name = new String(app.name);
          entry.nameLowerCase = app.name.toLowerCase();
        } else {
          MyLog.d("Set packageName [" + app.packageName + "]");
          entry.name = new String(app.packageName);
          entry.nameLowerCase = app.packageName.toLowerCase();
        }

        entry.labelLoaded = false;
        entry.icon = null;
        entry.uid = uid;
        entry.uidString = String.valueOf(uid);
        entry.packageName = new String(app.packageName);

        installedApps.add(entry);

        if(entryHash != null) {
          entryHash.name.concat("; " + entry.name);
        } else {
          installedAppsHash.put(sUid, entry);
        }
      }

      AppEntry entry = new AppEntry();
      entry.name = "Kernel";
      entry.nameLowerCase = "kernel";
      entry.icon = context.getResources().getDrawable(R.drawable.linux_icon);
      entry.packageName = entry.nameLowerCase;
      entry.labelLoaded = true;
      entry.uid = -1;
      entry.uidString = "-1";

      installedApps.add(entry);
      installedAppsHash.put("-1", entry);

      AppEntry entryHash = installedAppsHash.get("0");

      if(entryHash == null) {
        entry = new AppEntry();
        entry.name = "Root";
        entry.nameLowerCase = "root";
        entry.icon = context.getResources().getDrawable(R.drawable.root_icon);
        entry.packageName = entry.nameLowerCase;
        entry.labelLoaded = true;
        entry.uid = 0;
        entry.uidString = "0";

        installedApps.add(entry);
        installedAppsHash.put("0", entry);
      }

      handler.post(new Runnable() {
        public void run() {
          MyLog.d("Dismissing dialog");

          synchronized(dialogLock) {
            if(dialog != null) {
              dialog.dismiss();
              dialog = null;
            }
          }
        }
      });
      MyLog.d("Done getting installed apps");
    }
  }
}
