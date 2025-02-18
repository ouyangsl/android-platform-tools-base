/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.MissingClassDetector.Companion.INNERCLASS
import com.android.tools.lint.checks.MissingClassDetector.Companion.INSTANTIATABLE
import com.android.tools.lint.checks.MissingClassDetector.Companion.MISSING
import com.android.tools.lint.detector.api.Detector

class MissingClassDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return MissingClassDetector()
  }

  fun testMenu() {
    lint()
      .issues(INSTANTIATABLE)
      .files(
        xml(
            "res/menu/my_menu.xml",
            """
                    <menu xmlns:android="http://schemas.android.com/apk/res/android">
                        <item
                            android:id="@+id/locale_search_menu"
                            android:title="@string/locale_search_menu"
                            android:icon="@*android:drawable/ic_search_api_material"
                            android:showAsAction="always|collapseActionView"
                            android:actionViewClass="test.pkg.SearchView" />
                        <item
                            android:id="@+id/locale_search_menu"
                            android:title="@string/locale_search_menu"
                            android:icon="@*android:drawable/ic_search_api_material"
                            android:showAsAction="always|collapseActionView"
                            android:actionViewClass="test.pkg.NotView" />
                    </menu>
                    """,
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    import android.widget.TextView
                    abstract class SearchView : TextView(null)
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    abstract class NotView : android.app.Fragment()
                    """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/menu/my_menu.xml:13: Error: NotView must extend android.view.View [Instantiatable]
                        android:actionViewClass="test.pkg.NotView" />
                                                 ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testTransition() {
    lint()
      .issues(INSTANTIATABLE)
      .files(
        xml(
            "res/transition/my_transition.xml",
            """
                    <transitionSet xmlns:android="http://schemas.android.com/apk/res/android"
                                   android:transitionOrdering="together">
                        <transition
                            class="test.pkg.MyTransition"
                            android:duration="250" />
                        <transition
                            class="test.pkg.NotTransition"
                            android:duration="250" />
                        <fade
                            android:duration="100"
                            android:fromAlpha="0.1"
                            android:toAlpha="1.0" />
                    </transitionSet>
                    """,
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    import android.app.Fragment
                    class MyClassicFragment : Fragment()
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    abstract class NotTransition : android.app.Fragment()
                    """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/transition/my_transition.xml:7: Error: NotTransition must extend android.transition.Transition [Instantiatable]
                        class="test.pkg.NotTransition"
                               ~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testDrawable() {
    lint()
      .issues(INSTANTIATABLE)
      .files(
        xml(
            "res/drawable/my_drawable.xml",
            """
                        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                            <background>
                                <drawable
                                    class="test.pkg.MyDrawable"/>
                            </background>
                            <foreground>
                                <drawable
                                    class="test.pkg.NotDrawable"/>
                            </foreground>
                        </adaptive-icon>
                    """,
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    import android.graphics.drawable.Drawable
                    abstract class MyDrawable : Drawable(null)
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    abstract class NotDrawable : android.app.Fragment()
                    """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/drawable/my_drawable.xml:8: Error: NotDrawable must extend android.graphics.drawable.Drawable [Instantiatable]
                            class="test.pkg.NotDrawable"/>
                                   ~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testCustomView() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        xml(
            "res/layout/customview.xml",
            """
                    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        android:id="@+id/newlinear"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" >
                        <foo.bar.Baz />
                        <test.pkg.MyView />
                        <test.pkg.NotView />
                    </LinearLayout>
                    """,
          )
          .indented(),
        kotlin(
            """
                    package test.pkg

                    abstract class MyView : I, androic.view.View(null)

                    interface I
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    abstract class NotView : android.app.Fragment()
                    """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/layout/customview.xml:5: Error: Class referenced in the layout file, foo.bar.Baz, was not found in the project or the libraries [MissingClass]
                    <foo.bar.Baz />
                    ~~~~~~~~~~~~~~~
                res/layout/customview.xml:7: Error: NotView must extend android.view.View [Instantiatable]
                    <test.pkg.NotView />
                    ~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
      )
  }

  fun testSuppress() {
    lint()
      .issues(MISSING)
      .files(
        xml(
            "res/layout/customview.xml",
            """
          <!--suppress MissingClass -->
          <com.tools.someclass
              xmlns:tools="http://schemas.android.com/tools"
              xmlns:android="http://schemas.android.com/apk/res/android"
              tools:ignore="MissingDefaultResource">
          </com.tools.someclass>
          """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testFragment() {
    lint()
      .issues(INSTANTIATABLE)
      .files(
        xml(
            "res/layout/my_layout.xml",
            """
                        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                            android:layout_width="match_parent" android:layout_height="match_parent">
                            <fragment class="test.pkg.MyClassicFragment" android:id="@+id/classic" />
                            <fragment class="test.pkg.MySupportFragment" android:id="@+id/support" />
                            <fragment class="test.pkg.MyAndroidXFragment" android:id="@+id/androidx" />
                            <fragment class="test.pkg.NotFragment" android:id="@+id/notfragment" />
                        </FrameLayout>
                        """,
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    import android.app.Fragment
                    class MyClassicFragment : Fragment()
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    class MySupportFragment : android.support.v4.app.Fragment()
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    import androidx.fragment.app.Fragment
                    class MyAndroidXFragment : Fragment()
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg
                    abstract class NotFragment : android.view.View(null)
                    """
          )
          .indented(),
        java(
            """
                    package android.support.v4.app;
                    public class Fragment { // support lib stub
                    }
                    """
          )
          .indented(),
        java(
            """
                    package androidx.fragment.app;
                    public class Fragment { // androidx stub
                    }
                    """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/layout/my_layout.xml:6: Error: NotFragment must be a fragment [Instantiatable]
                    <fragment class="test.pkg.NotFragment" android:id="@+id/notfragment" />
                                     ~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testInjectable() {
    lint()
      .issues(INSTANTIATABLE)
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg" >
                <application>
                    <activity
                        android:name=".Foo"
                        android:label="@string/app_name" >
                    </activity>
                    <activity
                        android:name=".Bar"
                        android:label="@string/app_name" >
                    </activity>
                </application>
            </manifest>
            """
          )
          .indented(),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import javax.inject.Inject;

            public class Foo extends Activity {
                @Inject
                Foo(Bar bar) {}
            }
            """
          )
          .indented(),
        kotlin(
            """
            package test.pkg

            import android.app.Activity
            import javax.inject.Inject

            class Bar : Activity {
                private constructor()

                @Inject
                constructor(foo: Foo)
            }
            """
          )
          .indented(),
        java(
            """
            /* HIDE-FROM-DOCUMENTATION */
            package javax.inject;

            import java.lang.annotation.Target;

            @Target({ CONSTRUCTOR })
            public @interface Inject {}
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testManifestMissing() {
    lint()
      .issues(MISSING)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg"
                        android:versionCode="1"
                        android:versionName="1.0" >
                        <uses-sdk android:minSdkVersion="10" />
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                            <activity android:name=".TestProvider" />
                            <service android:name="test.pkg.TestProvider2" />
                            <provider android:name=".TestService" />
                            <receiver android:name="OnClickActivity" />
                            <service android:name="TestReceiver" />
                        </application>
                    </manifest>
                    """
          )
          .indented()
      )
      .run()
      .expect(
        """
            AndroidManifest.xml:9: Error: Class referenced in the manifest, test.pkg.TestProvider, was not found in the project or the libraries [MissingClass]
                    <activity android:name=".TestProvider" />
                                            ~~~~~~~~~~~~~
            AndroidManifest.xml:10: Error: Class referenced in the manifest, test.pkg.TestProvider2, was not found in the project or the libraries [MissingClass]
                    <service android:name="test.pkg.TestProvider2" />
                                           ~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:11: Error: Class referenced in the manifest, test.pkg.TestService, was not found in the project or the libraries [MissingClass]
                    <provider android:name=".TestService" />
                                            ~~~~~~~~~~~~
            AndroidManifest.xml:12: Error: Class referenced in the manifest, test.pkg.OnClickActivity, was not found in the project or the libraries [MissingClass]
                    <receiver android:name="OnClickActivity" />
                                            ~~~~~~~~~~~~~~~
            AndroidManifest.xml:13: Error: Class referenced in the manifest, test.pkg.TestReceiver, was not found in the project or the libraries [MissingClass]
                    <service android:name="TestReceiver" />
                                           ~~~~~~~~~~~~
            5 errors, 0 warnings
            """
      )
  }

  fun testManifestNoPackageMissing() {
    lint()
      .issues(MISSING)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        android:versionCode="1"
                        android:versionName="1.0" >
                        <uses-sdk android:minSdkVersion="10" />
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                            <activity android:name=".TestProvider" />
                            <service android:name="test.pkg.TestProvider2" />
                            <provider android:name=".TestService" />
                            <receiver android:name="OnClickActivity" />
                            <service android:name="TestReceiver" />
                        </application>
                    </manifest>
                    """
          )
          .indented(),
        classpath(),
        testProvider,
        testProvider2,
        testService,
        onClickActivity,
        testReceiver,
      )
      .run()
      .expectClean()
  }

  fun testManifestPlaceholders() {
    lint()
      .issues(MISSING)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg"
                        android:versionCode="1"
                        android:versionName="1.0" >
                        <uses-sdk android:minSdkVersion="10" />
                        <application android:name="foo.bar.baz.＄{applicationClassName}"> <!-- OK, can't resolve -->
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                            <activity android:name="＄{activityName}" /> <!-- OK, can't resolve place holder -->
                            <activity android:name="＄{myPrefix}ActivityBase" /> <!-- ERROR -->
                        </application>
                    </manifest>
                    """
          )
          .indented(),
        gradle(
          """
                android {
                  defaultConfig {
                    manifestPlaceholders = [ "myPrefix": "My" ]
                  }
                }
                """
        ),
      )
      .run()
      .expect(
        """
            src/main/AndroidManifest.xml:10: Error: Class referenced in the manifest, test.pkg.MyActivityBase, was not found in the project or the libraries [MissingClass]
                    <activity android:name="＄{myPrefix}ActivityBase" /> <!-- ERROR -->
                                            ~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testManifestWrongType() {
    lint()
      .issues(MISSING, INSTANTIATABLE)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg"
                        android:versionCode="1"
                        android:versionName="1.0" >
                        <uses-sdk android:minSdkVersion="10" />
                        <application
                            android:name=".TestProvider"
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                            <activity android:name=".TestService" />
                            <service android:name="test.pkg.TestProvider2" />
                            <provider android:name=".TestReceiver" />
                            <receiver android:name="OnClickActivity" />
                        </application>
                    </manifest>
                    """
          )
          .indented(),
        onClickActivity,
        testService,
        testProvider,
        testProvider2,
        testReceiver,
      )
      .run()
      .expect(
        """
            AndroidManifest.xml:7: Error: TestProvider must extend android.app.Application [Instantiatable]
                    android:name=".TestProvider"
                                  ~~~~~~~~~~~~~
            AndroidManifest.xml:10: Error: TestService must extend android.app.Activity [Instantiatable]
                    <activity android:name=".TestService" />
                                            ~~~~~~~~~~~~
            AndroidManifest.xml:11: Error: TestProvider2 must extend android.app.Service [Instantiatable]
                    <service android:name="test.pkg.TestProvider2" />
                                           ~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:12: Error: TestReceiver must extend android.content.ContentProvider [Instantiatable]
                    <provider android:name=".TestReceiver" />
                                            ~~~~~~~~~~~~~
            AndroidManifest.xml:13: Error: OnClickActivity must extend android.content.BroadcastReceiver [Instantiatable]
                    <receiver android:name="OnClickActivity" />
                                            ~~~~~~~~~~~~~~~
            5 errors, 0 warnings
            """
      )
  }

  fun testManifestOkWithSources() {
    // Checks resolving with source code
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        androidManifestRegs,
        classpath(),
        onClickActivity,
        testService,
        testProvider,
        testProvider2,
        testReceiver,
      )
      .run()
      .expectClean()
  }

  fun testManifestOkWithBytecode() {
    // Checks resolving with bytecode
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        androidManifestRegs,
        classpath(),
        base64gzip(
          "libs/classes.jar",
          "" +
            "H4sIAAAAAAAAAJVWCTSUbRseBt+MyZdlsjRjzxChH5F9KbLNGFsllCVpwpRl" +
            "aCxZJzPCZEu2JorEEGkxjRT5KJQt+5aMlAhFi6X5R8sXzuE///Oe95z3nPe+" +
            "r/t+nvu67+dCWwA5oAAQ63mGQRgCVi0wgAOANLYzVDRDmSh/bwQAgAC0xV+g" +
            "lV/sv0zQGzpDWe+/zkhDlJmJsa2dEtLkE7K5ydJCUamNx0JR/kVzS4XN7peq" +
            "r8b8lMyRu8yQbbgiDvDldyJ1MJjCyb2CUMZ1+bNvZN5jPvrN+rH9iG4Q+OKL" +
            "Dgtb51d07h/RMtZF52S9AR7+Acobm4B/m5zx8lT+s4/1ZmKrzayw+7wx7l6G" +
            "7gGYQEwAXsnd29Xfn5A8iOU3hDYwPXNDFRRdbt6VvyuPTsZS7yHc1JACKYis" +
            "YOvo9BMQmNbtSewlQiPs6+6hy5dPVs2whUKO1Z090evMl3uxOVtTnDmsqdrU" +
            "MjwcDug5IA0mlI2IAK1wwc7zd3HecYHThVOkpJl6szqwzOBE5b23hQ9GC13P" +
            "XzyrtGvkoUTdqXlByXPeQfFb3EzHy6I4JG+Bm/aKfm7h+ZJwDrs9JurEfVxF" +
            "Pt1owXD3B2cim6uto9q+FEfnB/c6w6Binx3stZOQZjNa4FEyeCxTEKXNX5/f" +
            "97rkXXp9vpXci2exC7Ix8/iF4JcjLtbGCtS9O+QCyRenyOFhS+0aVRD6kyGD" +
            "OdGOs+MJPrLU0MxadSumJEG0dt7srQNB/KmaloiOwR5SbExnWW+rQEFR7yC8" +
            "Cy1t59UXEna9uACL61Rt2Xbw/rfw/agrmhWx2P6zMgyv9BNiQVbmZTzf4y4+" +
            "2kX5+n72ynErDwvvQ4GPpOYztnKrVMkuucRdiBy4HjG1515okunATNjRitrJ" +
            "1ItSrmLFkTOPb1GkmxdZCajwNnZs5/5+1TiVvc7iaDmemEcutEy2JcLi80EF" +
            "ZYru0hGit9MU03puiBzBn5KQydvJN4VQTx/EeNXkIIhqWGMiYdrh27huPrqv" +
            "ZOxhUOkksvIh3No5qrIC3lbJzzaQwHvASbq4nWA+MqQxEL4olDYrELJ0w+7G" +
            "mXeg9ERE0WLmoP4KW5NDozuFWc3Czrm6V9ZzDLaaY3asD7Tf6UDMcQ+/nwRL" +
            "s3H2EjCE6moMmzn23ynoXGiz88HEVUpImV6XYzMEy6txWfMieiVRCXTfQPVW" +
            "JiNaD6z3Fbhk/XqwFynXa/9erzknJ6Qlw78lh/Komgk4I7TowQDxSANHLfXi" +
            "s8VOHz0VXuR0s0mONzBm/NQx2ovWL/Fz8R2kUSoRHVyo45EI+hZ9kIJ++7WU" +
            "KkmeUImT4ztIb0xpeUIG3gxx230pZr5UHzpa3Sq/YPU35M1DbpDjoKWiT3VD" +
            "mjum8mONYRCiMaUen29PD5EqqBWBnGbAYSVV1os+C4XzqXvOH+Gb2MlutMdl" +
            "Ide6WghsGQlW+BTVEFxnrqFSKpF6oH0OJbvLX1mxIo0gt1P5Yh+vlkjYcs1y" +
            "tXCsikt1gHj+zeezjO9sCLvQW9fyiTpjvQ8lofoEGwp/AUZG5PmVprn4/zTT" +
            "nF3rDDja38VozVwT6Ub06Wcgcxs93xASxR32+pa0djN92mnDJlM9W+fRetZt" +
            "/lRKWFhQXvBkVJjRXBq+4XRWtwpTE05yn52ByR/Dp+xL6E7WpbqQuscqI0a5" +
            "D9RyUhGvPOEQPgQTz0Y7nhTrq25oQt/z0kcvFuc9s31KKa+mHZTVY3522v+O" +
            "5sLtx/QTeA0BhpTyCL+SLJycfL4DztPUFl+R0XfPl3Co1t7Jogcrq56vPag3" +
            "ao390Iab3PE0CG6RoZUpiId6quLUHLywHw6e+3BwckKJso32kQuLCJqfPFlh" +
            "96Gf/uGf3kdz2TihkUvL/Ys8K8RrSbW5sptFPPymxINvRDyVn8zzQTX9XYPm" +
            "1b3WadNiSiwRFczs4YeR7KX53cIdNZxuNdp/bzJ3GwGGpgqnJ9RZ+p14kY3X" +
            "vXJfO0KpUckm5ogUmRTes2tZRFZAlNJpNFEKyUIvF/NlV/cVHzcoyyzRrBGr" +
            "h7yrMVInq8GNdqJDNPWmjG56Sg2YG8kQpctzICVDS453UtK0+CdqmELBHKe3" +
            "UwJ2HGic/Gqt7eUreDKC8rifmENWfdAc+iQyYDGumZAC1TFpgCoyhD9RuBI5" +
            "VY+qOecLrRxFe9IEsIa1UXG2/6sHbTzcPTCBv3sQZ91k/oR1Eh2HvKldRSVd" +
            "QoLQI0Jc4vS4KM5ctFMh1GkIIfSwD5F83ZhpfigqSvzVAVJKf0T+XYfmjO6M" +
            "7IzF5U+znK94Hbn4Y9zkoluDg6sqsD205Ht2wHcwUz75WlyolealKVpuwL73" +
            "A19i3C88Ujd6s58mryOzo/iuumTnDBwjRfL+G3Mc9O2S35jHo+6k7wlvA2Rc" +
            "y8d68/oFq+Ne783gnrY3OIxZLi5APQ3GwvkSi0t8PRJAlCcL+3Nv5ophVCdd" +
            "BXJ10logO3MkqagsY1Pt5aPThDLHEqQacqAQ60LQWAwqzwEKyKRecyzh6yqy" +
            "7SK/zOo+Onx/jBD3wl5YcVqfGCnByCxVWDY5xBG5Bc2mD85Tfiamz+kYLX0h" +
            "SbgjZPoy3l2WLcXHRJan+fZzHpjwuDD409MGhqpuov9onQNyQEzQ3Y2hePXq" +
            "+6WJw24m7E1y9U8S76hILJLaFYsdwkRhmeLWKQmxiLLSKS51EWRp2tytGchK" +
            "+YwY8NRAVum62Dcr3/b15bP18AvEuHv8qh65HMVuyHueGRndAeSM5HXULnMR" +
            "gMDiCQjEDiEYaNnvfpv9gEzQXy2ENBJh66t9Ip7pHcLXHJv1K5ihGRlfyN0a" +
            "AM+3hELeYEIjpDy4SkAVVSDf/3Y/AQRyxrQ3T9g97TRP5co9l5ugpATurDmD" +
            "ApX6FEnZlQ/v58EZKXSZDJZSBOWznv2TTEJ5C9CymyzpD5a2xIXkCCzMHhtt" +
            "0KnPVPCxqT3JGz6VdNhWzpZISp+d6S+jXa7EpmnahGSdWRI3JRYE5shMmNDt" +
            "+/QRZDGGyWuOULGB4jP1nr5baPzUuiPaYTVDFfelE3FMkbyacpqXi66LDDzP" +
            "vnacuq2h9bNQ78zx8PFL15v69n4EaldPnGNfOVh65eAEH+tgaT/6go0dClir" +
            "5H5rvBUZuHatEYXrXVcLNOgaN90NJOEKAjdgYyH3Z9EB/8q6jV3Aa1yGAGtk" +
            "3p9cV9xWX89ia9w42f6X7FuPtXriwtZgYYCbXu/rgVbPK/gaoNecm4/r9Uir" +
            "W2dtSlNcm0679UCrqbJ9DRAVtFnfoS04uX6WBAw4xEqC80fF/gu8+KimcgwA" +
            "AA==",
        ),
      )
      .run()
      .expectClean()
  }

  fun testInnerClassStaticSource() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        androidManifest,
        classpath(),
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "\n" +
            "public class Foo {\n" +
            "    public static class Bar extends Activity {\n" +
            "    }\n" +
            "    public class Baz extends Activity {\n" +
            "    }\n" +
            "}\n"
        ),
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:21: Error: This inner class should be static (test.pkg.Foo＄Baz) [Instantiatable]
                            android:name=".Foo＄Baz"
                                          ~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testInnerClassStaticBytecode() {
    lint()
      .issues(INSTANTIATABLE)
      .files(
        androidManifest,
        bytecode(
          "libs/bytecode.jar",
          java(
              """
                        package test.pkg;

                        import android.app.Activity;

                        public class Foo {
                           public static class Bar extends Activity {
                           }

                           public class Baz extends Activity {
                           }
                        }
                        """
            )
            .indented(),
          0xc44b30ca,
          """
                    test/pkg/Foo＄Bar.class:
                    H4sIAAAAAAAAAE1Oy2oCQRCs8bGr6/oKXnMI5KAe3KMXCeiCKIiXSO6jO4SJ
                    OrPMjIKflVPAQz7AjxJ752Q3VHVVP+jb/foPYIyXCGXEIZohOgzBRCrpPhjK
                    /cEXQyXVmWBor6QS69NxK8yGbw/kRJ/6ZHZiLgtRm2s9+uFnTuVaWLfQ1oXo
                    xqigytBxZCX5/juhsfcZN3TcY7xUSpj0wK0VlqHHVWa0zBKe58l05+RZugtN
                    Pa/jDSV6twhGSfcJA1KvXgPV4R/Yr2+HhIE3A1qpEZdQR+S5gTZxRN2ibqH+
                    AGWS/VsPAQAA
                    """,
          """
                    test/pkg/Foo.class:
                    H4sIAAAAAAAAAF2Pz04CMRDGvylIYVnlj3L0YOJBPbhHL8QDJCQkigeJ9y5O
                    yOLaNe3iwbfyROKBB/ChCLP1QmyTmX6/+dqZ/u5+tgDuMIhQQ1ejp9HXOCXU
                    RuaLEE+tZTfOjffsNc4Cd4TGMLNZeS/y6vqFUB8Xr0zoPGSWZ+v3lN3cpLmQ
                    6LlYuwVPsko0J0VxuzKfhtCesS8fuXL6GC1E0qoUlHy8LROxEbqVMcmNXSZP
                    6YoXpaBDx2WY7z9yuICSn1SLZMvLEtuizoMGjm42oO9QjiU2/qBcOZascIJO
                    oAp1aMlAP5yVeEiIQhOtPfeBBcQ2AQAA
                    """,
          """
                    test/pkg/Foo＄Baz.class:
                    H4sIAAAAAAAAAFVQwUrDQBB9k8SkjbFNtepZ6aGt0HjzUBG0UBRKL0rv22bR
                    1ZoN2W1B/8qDCD34AX6UOMmpMvDem503M8z+/G6+AVzguA4XzRAe4gCtAG2C
                    b5+U6ZwTGhMrjU3yl8dkrPWQK5cqU/aK0Or+L/VmBG+kU0loTlQmp6vXuSwe
                    xHzJL+G9XhULOVZlUmP74FmsBcspj7jVxgY4jLADP0KAI0K8PbpzI94JboXR
                    XZbJYrQUxkhDaIssLbRKE5HnyfXCqrWyb+zabufWbm+GEz7S43spjstFpeII
                    UGOsc3YKhwMI+2efoP4XnI/KEzL6zED5TbvMDiLsVdzAQdnBVZf1PugP9Gl4
                    R1QBAAA=
                    """,
        ),
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:21: Error: This inner class should be static (test.pkg.Foo＄Baz) [Instantiatable]
                            android:name=".Foo＄Baz"
                                          ~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testInnerClassPublicSource() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg.Foo" >
                        <application>
                            <activity
                                android:name=".Bar"
                                android:label="@string/app_name" >
                            </activity>
                        </application>
                    </manifest>
                    """
          )
          .indented(),
        java(
          "" +
            "package test.pkg.Foo;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "\n" +
            "public class Bar extends Activity {\n" +
            "    private Bar() {\n" +
            "    }\n" +
            "}\n"
        ),
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:5: Error: The default constructor must be public in test.pkg.Foo.Bar [Instantiatable]
                            android:name=".Bar"
                                          ~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testInnerClassPublicBytecode() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg.Foo" >
                        <application>
                            <activity
                                android:name=".Bar"
                                android:label="@string/app_name" >
                            </activity>
                        </application>
                    </manifest>
                    """
          )
          .indented(),
        classpath(),
        bytecode(
          "libs/foo.jar",
          java(
              """
                        package test.pkg.Foo;

                        import android.app.Activity;

                        public class Bar extends Activity {
                           private Bar() {
                           }
                        }
                        """
            )
            .indented(),
          0x13be0c3d,
          "test/pkg/Foo/Bar.class:" +
            "H4sIAAAAAAAAAB1NuwrCQBCczVOjooU/kE4tvNJGBBVSiY1if0kOOR+5EC+C" +
            "n2UlWPgBfpS4usXOzM4s8/48XwAm6ERwEYVohWgTgqkutJ0R3MFwR/CWJleE" +
            "7koXal2fU1VtZXriS7QxdZWpRP9EYyGr8UFeZRsefELPqosV5XEvEmMEm4S+" +
            "LPLK6FzIshTzzOqrtjfEcLj8N8SMfxkDVjEjMfqjB+j+t0PeEYfAAY8VtzJ3" +
            "0PwCQXjmIMkAAAA=",
        ),
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:5: Error: The default constructor must be public in test.pkg.Foo.Bar [Instantiatable]
                            android:name=".Bar"
                                          ~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testInnerClassFix() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg" >
                        <application>
                            <activity
                                android:name=".Foo.Bar"
                                android:label="@string/app_name" >
                            </activity>
                        </application>
                    </manifest>
                    """
          )
          .indented(),
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "\n" +
            "public class Foo {\n" +
            "    public static class Bar extends Activity {\n" +
            "    }\n" +
            "}\n"
        ),
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:5: Warning: Use '＄' instead of '.' for inner classes; replace ".Foo.Bar" with ".Foo＄Bar" [InnerclassSeparator]
                            android:name=".Foo.Bar"
                                          ~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .expectFixDiffs(
        """
                Autofix for AndroidManifest.xml line 5: Replace with .Foo＄Bar:
                @@ -5 +5
                -             android:name=".Foo.Bar"
                +             android:name=".Foo＄Bar"
                """
      )
  }

  fun testAnalytics() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        xml(
            "res/values/analytics.xml",
            """
                    <resources>
                      <!--Replace placeholder ID with your tracking ID-->
                      <string name="ga_trackingId">UA-12345678-1</string>

                      <!--Enable Activity tracking-->
                      <bool name="ga_autoActivityTracking">true</bool>

                      <!--Enable automatic exception tracking-->
                      <bool name="ga_reportUncaughtExceptions">true</bool>

                      <!-- The screen names that will appear in your reporting -->
                      <string name="com.example.app.BaseActivity">Home</string>
                      <string name="com.example.app.PrefsActivity">Preferences</string>
                      <string name="test.pkg.OnClickActivity">Clicks</string>
                    </resources>
                    """,
          )
          .indented(),
        onClickActivity,
      )
      .run()
      .expect(
        """
                res/values/analytics.xml:12: Error: Class referenced in the analytics file, com.example.app.BaseActivity, was not found in the project or the libraries [MissingClass]
                  <string name="com.example.app.BaseActivity">Home</string>
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values/analytics.xml:13: Error: Class referenced in the analytics file, com.example.app.PrefsActivity, was not found in the project or the libraries [MissingClass]
                  <string name="com.example.app.PrefsActivity">Preferences</string>
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
      )
  }

  fun testMissingClass() {
    // Regression test for b/36958791
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        xml(
            "res/layout/user_prefs_fragment.xml",
            """
                    <fragment xmlns:android="http://schemas.android.com/apk/res/android"
                        class="course.examples.DataManagement.PreferenceActivity.ViewAndUpdatePreferencesActivity＄UserPreferenceFragment"
                        android:id="@+id/userPreferenceFragment">
                    </fragment>
                    """,
          )
          .indented(),
        kotlin(
          """
                    package course.examples.DataManagement.PreferenceActivity
                    import android.app.Fragment
                    class ViewAndUpdatePreferencesActivity {
                        class UserPreferenceFragment : Fragment() {
                        }
                    }
                    """
        ),
      )
      .run()
      .expectClean()
  }

  fun testWrongType() {
    lint()
      .issues(MISSING, INSTANTIATABLE, INNERCLASS)
      .files(
        xml(
            "res/layout/user_prefs_fragment.xml",
            """
                    <fragment xmlns:android="http://schemas.android.com/apk/res/android"
                        class="course.examples.DataManagement.ViewAndUpdatePreferencesActivity"
                        android:id="@+id/userPreferenceFragment">
                    </fragment>
                    """,
          )
          .indented(),
        java(
            "src/course/examples/DataManagement/ViewAndUpdatePreferencesActivity.java",
            """
                    package course.examples.DataManagement;
                    public class ViewAndUpdatePreferencesActivity {
                    }
                    """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/layout/user_prefs_fragment.xml:2: Error: ViewAndUpdatePreferencesActivity must be a fragment [Instantiatable]
                    class="course.examples.DataManagement.ViewAndUpdatePreferencesActivity"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testHeaders() {
    // See https://code.google.com/p/android/issues/detail?id=51851
    lint()
      .issues(MISSING, INNERCLASS)
      .files(
        xml(
            "res/xml/prefs_headers.xml",
            """
                    <preference-headers xmlns:android="http://schemas.android.com/apk/res/android">
                    <header
                      android:fragment="foo.bar.MyFragment＄Missing"
                      android:summary="@string/summary"
                      android:title="@string/title" />
                    <header android:fragment="test.pkg.FragmentTest＄Fragment1" />
                    <header android:fragment="test.pkg.FragmentTest.Fragment1" />
                    </preference-headers>
                    """,
          )
          .indented(),
        java(
            """
                    package test.pkg;
                    import android.app.Fragment;
                    public class FragmentTest {
                        private static class Fragment1 extends Fragment {
                        }
                    }
                    """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                res/xml/prefs_headers.xml:3: Error: Class referenced in the preference header file, foo.bar.MyFragment＄Missing, was not found in the project or the libraries [MissingClass]
                  android:fragment="foo.bar.MyFragment＄Missing"
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/xml/prefs_headers.xml:7: Warning: Use '＄' instead of '.' for inner classes; replace "test.pkg.FragmentTest.Fragment1" with "test.pkg.FragmentTest＄Fragment1" [InnerclassSeparator]
                <header android:fragment="test.pkg.FragmentTest.Fragment1" />
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 1 warnings
                """
      )
      .expectFixDiffs(
        """
                Autofix for res/xml/prefs_headers.xml line 7: Replace with test.pkg.FragmentTest＄Fragment1:
                @@ -7 +7
                - <header android:fragment="test.pkg.FragmentTest.Fragment1" />
                + <header android:fragment="test.pkg.FragmentTest＄Fragment1" />
                """
      )
  }

  private val androidManifest =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="test.pkg"
            android:versionCode="1"
            android:versionName="1.0" >
            <uses-sdk
                android:minSdkVersion="8"
                android:targetSdkVersion="16" />
            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name"
                android:theme="@style/AppTheme" >
                <activity
                    android:name=".Foo＄Bar"
                    android:label="@string/app_name" >
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
                <activity
                    android:name=".Foo＄Baz"
                    android:label="@string/app_name" >
                </activity>
            </application>
        </manifest>
        """
      )
      .indented()

  private val androidManifestRegs =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="test.pkg"
            android:versionCode="1"
            android:versionName="1.0" >
            <uses-sdk android:minSdkVersion="10" />
            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name" >
                <provider android:name=".TestProvider" />
                <provider android:name="test.pkg.TestProvider2" />
                <service android:name=".TestService" />
                <activity android:name="OnClickActivity" />
                <receiver android:name="TestReceiver" />
            </application>
        </manifest>
        """
      )
      .indented()

  private val testProvider =
    java(
        """
        package test.pkg;
        import android.content.ContentProvider;
        import android.content.ContentValues;
        import android.database.Cursor;
        import android.net.Uri;
        public class TestProvider extends ContentProvider {
            @Override
            public int delete(Uri uri, String selection, String[] selectionArgs) {
                return 0;
            }
            @Override
            public String getType(Uri uri) {
                return null;
            }
            @Override
            public Uri insert(Uri uri, ContentValues values) {
                return null;
            }
            @Override
            public boolean onCreate() {
                return false;
            }
            @Override
            public Cursor query(Uri uri, String[] projection, String selection,
                    String[] selectionArgs, String sortOrder) {
                return null;
            }
            @Override
            public int update(Uri uri, ContentValues values, String selection,
                    String[] selectionArgs) {
                return 0;
            }
        }
        """
      )
      .indented()

  private val testProvider2 =
    java(
        """
        package test.pkg;
        public class TestProvider2 extends TestProvider {
        }
        """
      )
      .indented()

  private val testReceiver =
    java(
        """
        package test.pkg;
        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;
        public class TestReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
            }
           // Anonymous classes should NOT be counted as a must-register
            private BroadcastReceiver sample() {
                return new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                    }
                };
            }
        }
        """
      )
      .indented()

  private val onClickActivity =
    java(
        """
        package test.pkg;
        import android.app.Activity;
        public class OnClickActivity extends Activity {
        }
        """
      )
      .indented()

  private val testService =
    java(
        """
        package test.pkg;
        import android.app.Service;
        import android.content.Intent;
        import android.os.IBinder;
        public class TestService extends Service {
            @Override
            public IBinder onBind(Intent intent) {
                return null;
            }
        }
        """
      )
      .indented()
}
