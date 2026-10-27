"""Evaluation-only delta for HushGram's GPL-3.0-only native runner.
Copyright 2026 HushGram contributors (upstream runner) and Scroll Guard lab contributors.
This modifies test instrumentation only; no HushGram code enters Scroll Guard.
"""
from pathlib import Path
p=Path('hushgram/scripts/native-runner/src/app/hushgram/nativerunner/Runner.java')
s=p.read_text()
needle='            case "presence":'
assert needle in s
s=s.replace(needle,'            case "reel-input": return reelInput(target, out);\n            case "home-filters": return homeFilters(target, out);\n'+needle,1)
methods=r'''
    // Lab-only: exercise the real patched ViewPager2 setter on Android's main thread.
    // This does not claim a physical swipe or a signed-in Instagram viewer was tested.
    private static boolean reelInput(Context target, Bundle out) throws Exception {
        ClassLoader loader = target.getClassLoader();
        Class<?> utils = loader.loadClass(EXTENSION_CLASS);
        utils.getMethod("setContext", Context.class).invoke(null, target);
        Class<?> settings = loader.loadClass(SETTINGS_CLASS);
        Object stop = settings.getField("STOP_REELS_SCROLLING").get(null);
        save(stop, true);
        Class<?> reel = loader.loadClass("app.hushgram.extension.instagram.reels.ReelScrolling");
        Class<?> pager = loader.loadClass("androidx.viewpager2.widget.ViewPager2");
        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<Boolean>(new java.util.concurrent.Callable<Boolean>() {
          @Override public Boolean call() throws Exception {
            Object guarded = pager.getConstructor(Context.class).newInstance(target);
            Object ordinary = pager.getConstructor(Context.class).newInstance(target);
            Method setter = pager.getMethod("setUserInputEnabled", boolean.class);
            // Verified from this pinned stock APK's actual DEX: setUserInputEnabled
            // stores its boolean parameter in ViewPager2.A09. The public getter is stripped.
            if (target.getPackageManager().getPackageInfo(target.getPackageName(),0).getLongVersionCode()!=385811006L)
                throw new IllegalStateException("Unmapped pager field for this APK");
            Field input = pager.getDeclaredField("A09");
            input.setAccessible(true);
            setter.invoke(guarded, true);
            boolean initial = input.getBoolean(guarded);
            reel.getMethod("pager", Object.class).invoke(null, guarded);
            // Do not apply the helper's return value ourselves: the injected setter must act.
            for (int i=0; i<50; i++) setter.invoke(guarded, true);
            boolean blocked = !input.getBoolean(guarded);
            setter.invoke(ordinary, true);
            boolean unrelated = input.getBoolean(ordinary);
            save(stop, false);
            setter.invoke(guarded, true);
            boolean restored = input.getBoolean(guarded);
            out.putString(PREFIX+"reelInput.initial", ""+initial);
            out.putString(PREFIX+"reelInput.blockedAfter50EnableAttempts", ""+blocked);
            out.putString(PREFIX+"reelInput.unregisteredPagerUsable", ""+unrelated);
            out.putString(PREFIX+"reelInput.offRestoresInput", ""+restored);
            out.putString(PREFIX+"scope", "real patched setter; no signed-in feed or physical gestures");
            return initial && blocked && unrelated && restored;
          }
        });
        new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
        return task.get(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    public enum LabKind { CLIPS_NETEGO, IMMERSIVE_SEGUE_ITEM, VIBES_IN_FEED_UNIT, HATCH_IMMERSIVE_IN_FEED_UNIT, MEDIA }
    public static class LabItem { public LabKind kind; LabItem(LabKind kind) {this.kind=kind;} }

    // Lab-only synthetic inputs to actual installed extension methods, not Instagram feed data.
    private static boolean homeFilters(Context target, Bundle out) throws Exception {
        ClassLoader loader=target.getClassLoader();
        loader.loadClass(EXTENSION_CLASS).getMethod("setContext", Context.class).invoke(null,target);
        Class<?> settings=loader.loadClass(SETTINGS_CLASS);
        Object hide=settings.getField("HIDE_FEED_REELS").get(null);
        Method filter=loader.loadClass("app.hushgram.extension.instagram.reels.FeedReels").getMethod("filter",Object.class);
        boolean passed=true;
        for (LabKind kind:LabKind.values()) {
            Object item=new LabItem(kind);
            save(hide,false); boolean off=filter.invoke(null,item)==item;
            save(hide,true); boolean on=filter.invoke(null,item)==(kind==LabKind.MEDIA ? item : null);
            out.putString(PREFIX+"synthetic."+kind, "offRetains="+off+",onExpected="+on);
            passed &= off && on;
        }
        save(hide,false);
        Object home=settings.getField("HIDE_HOME_FEED").get(null);
        Method all=loader.loadClass("app.hushgram.extension.instagram.feed.HomeFeed").getMethod("filter",Object.class);
        Object item=new LabItem(LabKind.MEDIA);
        save(home,false); boolean off=all.invoke(null,item)==item;
        save(home,true); boolean on=all.invoke(null,item)==null;
        save(home,false);
        out.putString(PREFIX+"synthetic.wholeHome", "offRetains="+off+",onRemoves="+on);
        out.putString(PREFIX+"scope", "synthetic input objects; real APK helpers; no server feed parser or Story tray exercised");
        return passed && off && on;
    }

'''
needle='    private static boolean presence(Context target, Bundle out) throws Exception {'
assert needle in s
s=s.replace(needle,methods+needle,1);p.write_text(s)
p=Path('hushgram/scripts/native-runner-device.ps1');s=p.read_text()
needle="$NativeRunnerFeatureCases = [ordered]@{ 'presence' = $null;"
assert needle in s
s=s.replace(needle,"$NativeRunnerFeatureCases = [ordered]@{ 'reel-input' = $null; 'home-filters' = $null; 'presence' = $null;",1)
p.write_text(s)
print('Added two evaluation-only cases; production extension/APK unchanged.')
