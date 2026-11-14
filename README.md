# יוצר אפליקציות — smti.uk/apk

אתר שהופך אתר אחד או כמה אתרים לאפליקציית אנדרואיד סגורה (APK).

- **האתר** (`src/`, `wrangler.toml`) הוא Cloudflare Worker שנפרס אוטומטית מגיטהאב. הוא משתמש במסד `drive-pro`, אבל רק בטבלאות משלו: `apk_jobs`, `apk_chunks` ו-`apk_meta`.
- **הבנייה** (`.github/workflows/build-apk.yml`) רצה ב-GitHub Actions. האתר מפעיל אותה, והיא בונה את האפליקציה ומחזירה את הקובץ לאתר.
- **האפליקציה** (`android/`) תומכת בכמה אתרים מורשים עם מסך בית, בכתובות נוספות, ובהגדרות נעולות בקוד.

## הגדרה חד-פעמית

1. **ב-Cloudflare** (Worker בשם `smt-apk` ← Settings ← Variables and Secrets), להוסיף שני סודות מסוג Secret:
   - `GH_TOKEN`: טוקן fine-grained של גיטהאב על המאגר הזה, עם הרשאת **Actions: Read and write**.
   - `BUILD_SECRET`: מחרוזת סודית ארוכה כלשהי.
2. **בגיטהאב** (Settings ← Secrets and variables ← Actions), להוסיף את הסוד `APK_BUILD_SECRET` עם אותה מחרוזת מסעיף 1.

## הגבלות

- עד 6 בניות לשעה לכל כתובת IP, ועד 60 ביום בסך הכול.
- קבצים ובקשות נמחקים אחרי 7 ימים.
- החסימה חלה על מעבר בין דפים. תוכן שנטען בתוך דף מורשה לא נחסם.
