Focus — Android Design System & Developer Handoff
Platform: Kotlin + Jetpack Compose · Reference device: Samsung Galaxy A32 (SM-A325) · Android 11–13 · Design language: monochrome Liquid Glass · All dimensions in dp unless marked sp or otherwise specified.
This specification consolidates the Focus screens described in this chat: Home, setup and permissions, Focus sessions, early exit, session completion, App Rules, Allowed Callers, Allowed Apps, Time Bank, Accountability Mirror, Cookie Jar, Rank, Stats, Profile, and Security.
Specification convention: values explicitly established by the design brief are treated as design requirements. Values not previously defined numerically are marked (est.) and should be validated against the actual Compose implementation. No accent colours are introduced; the interface remains black, white and neutral grey.

1. Canvas, device and scaling
1.1 Target device
Property	Specification
Device	Samsung Galaxy A32, SM-A325
Physical resolution	1080 × 2400 px
Reference density-independent canvas	411 × 914 dp (est.)
Original mockup	390 × 844 dp
Aspect ratio	20:9 nominal
Display	AMOLED
OS support	Android 11–13
UI framework	Kotlin + Jetpack Compose
Orientation	Portrait
Minimum supported content width	320 dp (est.)
Design reference density	1 dp = 1 logical pixel in the design canvas, not 1 physical pixel
The Galaxy A32's actual logical density and available content height depend on display scaling, system bars and font/display-size settings. Treat 411 × 914 dp as the target reference canvas, not a guarantee that every device configuration reports exactly those dimensions.
1.2 Scaling from 390 dp to 411 dp
The width scale factor is:
\[
s_w = \frac{411}{390} \approx 1.05385
\]

That means the target canvas is approximately 5.38% wider than the mockup.
Original mockup value	Galaxy A32 canvas equivalent	Rule
390 dp canvas width	411 dp	Use available width
16 dp horizontal gutter	16 dp	Keep fixed
12 dp card gap	12 dp	Keep fixed
24 dp card radius	24 dp	Keep fixed
56 dp primary button height	56 dp	Keep fixed
48 dp minimum touch target	48 dp	Keep fixed
200 dp content element width	211 dp (est.)	Scale only if proportionally sized
300 dp content element width	316 dp (est.)	Scale only if proportionally sized
390 dp full-width card	379 dp	Recalculate from gutters
844 dp mockup height	914 dp nominal	Use insets and scrolling, not vertical scaling
Important implementation rule: do not scale every dp value by 1.05385. Typography, spacing, radii, touch targets and component heights should remain at their defined dp sizes. Let full-width components expand naturally. Scale only dimensions explicitly intended to be proportional, such as a chart viewport or a fixed-width illustration.
With 16 dp side gutters, the usable content width is:
\[
411 - 16 - 16 = 379\text{ dp}
\]

1.3 Safe areas and system bars
Use edge-to-edge layout with explicit inset handling. The app background should extend behind system bars, while interactive content stays clear of them.
Area	Specification
Status bar	Transparent over black background
Status-bar icon colour	White/light icons
Top safe inset	Use WindowInsets.statusBars
Bottom navigation inset	Use WindowInsets.navigationBars
Gesture navigation	Draw behind the system navigation area; reserve its inset for content
Three-button navigation	Respect the larger system navigation inset automatically
Keyboard	Apply imePadding() to fields and bottom sheets
Horizontal safe inset	Respect WindowInsets.safeDrawing where appropriate
Screen content	Scrollable column; do not force content into 914 dp
Tab bar	Float above the bottom navigation inset
Recommended Compose setup
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            FocusTheme {
                FocusApp()
            }
        }
    }
}


For a screen scaffold:
Scaffold(
    containerColor = FocusColors.Background,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = {
        FocusBottomBar()
    }
) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // Screen content
    }
}


Choose one inset strategy per edge. Do not combine safeDrawing padding, manual status-bar padding and scaffold inset padding on the same edge, or the content will be double-inset.
For the floating tab bar, place it above the bottom system inset with a separate navigationBars calculation rather than letting the bar overlap the gesture handle.

2. Monochrome colour tokens
All colour values below are opaque hex values with alpha specified separately. For Compose, #FFFFFF at 40% alpha means white with alpha 0x66, not a new grey hex colour.
2.1 Surfaces
Token	Hex	Alpha	Usage
background	#000000	100%	Main app canvas
surface.card	#0B0B0C	100%	Standard cards
surface.raised	#141416	100%	Raised cards and grouped surfaces
surface.inset	#1C1C1E	100%	Fields, icon tiles, inset lists
surface.track	#2A2A2D	100%	Ring tracks, bar backgrounds
surface.pressed	#242427	100%	Pressed dark surface
surface.disabled	#0B0B0C	60%	Disabled surface
surface.glassTint	#0B0B0C	82% (est.)	Floating tab bar
surface.sheetTint	#1C1C1E	94% (est.)	Bottom-sheet fill
2.2 Text and icons
Token	Hex	Alpha	Usage
text.primary	#FFFFFF	100%	Hero values, main headings
text.high	#FFFFFF	70%	Secondary emphasis
text.secondary	#A1A1A6	100%	Descriptions and metadata
text.tertiary	#636366	100%	Hints and low-priority labels
text.dim	#FFFFFF	40%	Completed agenda items
text.muted	#FFFFFF	20%	Inactive chart values
icon.primary	#FFFFFF	100%	Standard icon
icon.inactive	#FFFFFF	40%	Inactive navigation/icon state
icon.inverted	#000000	100%	Icons on white active tiles
text.disabled	#FFFFFF	30% (est.)	Disabled labels
Use alpha to express hierarchy consistently. Do not substitute coloured status indicators: granted permissions, active rules and success states use white glyphs, outlines or filled surfaces.
2.3 Borders, tracks and visualisations
Token	Hex	Alpha	Usage
border.hairline	#FFFFFF	10%	Card and grouped-row borders
border.specular	#FFFFFF	8%	Top-edge card highlight
border.focus	#FFFFFF	70%	Focused field and active item
border.active	#FFFFFF	100%	Active pill or selection
chart.primary	#FFFFFF	100%	Main chart data
chart.secondary	#FFFFFF	35%	Other days and comparison data
chart.tertiary	#FFFFFF	20%	Low-priority data
chart.mid	#FFFFFF	70%	Intermediate chart layer
chart.inner	#FFFFFF	40%	Inner ring/radar layer
chart.grid	#FFFFFF	10%	Gridlines
route.primary	#FFFFFF	100%	Strava route line
route.halo	#FFFFFF	25%	Route glow
ring.track	#2A2A2D	100%	Inactive ring track
scrim	#000000	64% (est.)	Modal background dim
disabled.overlay	#000000	30% (est.)	Disabled content
2.4 Glow, glass and grain tokens
Token	Colour	Alpha	Use
glow.core	#FFFFFF	25%	Hero-number bloom
glow.subtle	#FFFFFF	12%	Active pill/button glow
glow.celebration	#FFFFFF	25%	Session-complete burst
glass.border	#FFFFFF	10%	Tab bar/sheet border
glass.highlight	#FFFFFF	8%	Upper edge highlight
grain.overlay	#FFFFFF	3% (est.)	Fine film-grain texture
Compose token example
object FocusColors {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF0B0B0C)
    val Raised = Color(0xFF141416)
    val Inset = Color(0xFF1C1C1E)
    val Track = Color(0xFF2A2A2D)

    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFA1A1A6)
    val Tertiary = Color(0xFF636366)

    val Hairline = Color.White.copy(alpha = 0.10f)
    val Specular = Color.White.copy(alpha = 0.08f)
    val Dim = Color.White.copy(alpha = 0.40f)
    val Muted = Color.White.copy(alpha = 0.20f)

    val GlowCore = Color.White.copy(alpha = 0.25f)
    val GlowSubtle = Color.White.copy(alpha = 0.12f)
}



3. Typography
3.1 Font decision
- Visual reference: Apple SF Pro Display / SF Pro Text.
- Closest free alternative: Inter.
- Android production font: Inter, packaged locally in the app.
- System fallback: Roboto.
- Numerals: use Inter Light or Inter ExtraLight where available; do not fake thin numerals with opacity.
- Licensing: do not package Apple's SF Pro files in an Android app unless the applicable Apple licence expressly permits that use.
The typography sizes below are (est.) implementation values based on the visual hierarchy in the mockups.
3.2 Type scale
Style	Font family	Free alternative	Weight	Size	Line height	Letter spacing
Hero numeral	SF Pro Display	Inter	300 / Light	64 sp	72 sp	−1.8 sp
Large hero numeral	SF Pro Display	Inter	300 / Light	56 sp	64 sp	−1.5 sp
Metric numeral	SF Pro Display	Inter	300 / Light	36 sp	42 sp	−0.8 sp
Screen title	SF Pro Display	Inter	500 / Medium	30 sp	36 sp	−0.6 sp
Section title	SF Pro Display	Inter	500 / Medium	20 sp	26 sp	−0.2 sp
Card title	SF Pro Text	Inter	500 / Medium	17 sp	22 sp	0 sp
Body	SF Pro Text	Inter	400 / Regular	14 sp	20 sp	0 sp
Body emphasis	SF Pro Text	Inter	500 / Medium	14 sp	20 sp	0 sp
Caption	SF Pro Text	Inter	400 / Regular	12 sp	16 sp	0.1 sp
Tracked caps	SF Pro Text	Inter	600 / SemiBold	10 sp	14 sp	1.4 sp
Button	SF Pro Text	Inter	600 / SemiBold	15 sp	20 sp	0 sp
Tab label	SF Pro Text	Inter	500 / Medium	10 sp	12 sp	0.1 sp
Text field	SF Pro Text	Inter	400 / Regular	16 sp	24 sp	0 sp
Field hint	SF Pro Text	Inter	400 / Regular	14 sp	20 sp	0 sp
Chart label	SF Pro Text	Inter	500 / Medium	10 sp	14 sp	0.2 sp
Typography rules
1. Use fontFeatureSettings = "tnum" for timers, distances, times and numeric counters where supported.
2. Use tabular numerals for live countdowns to prevent horizontal jitter.
3. Avoid applying transparency to the font to simulate a lighter weight.
4. Tracked caps are for short labels such as FOCUS TIME, POWERED BY STRAVA and UNCOMMON.
5. Respect Android's user font scaling. Do not hardcode pixel heights around text; allow line height and content to expand.

4. Spacing, gutters and corner radii
4.1 Spacing scale
Token	Value
space.0	0 dp
space.1	4 dp
space.2	8 dp
space.3	12 dp
space.4	16 dp
space.5	20 dp
space.6	24 dp
space.8	32 dp
space.10	40 dp
space.12	48 dp
4.2 Layout rules
Property	Value
Screen horizontal gutter	16 dp
Full-width card gap	12 dp
Section-to-section gap	24 dp
Card internal padding	16 dp
Dense row vertical padding	12 dp
Standard row vertical padding	14 dp (est.)
Icon-to-label gap	8 dp
Label-to-value gap	4 dp
Button-to-button gap	12 dp
Minimum touch target	48 × 48 dp
Two-column gap	12 dp
Grid tile gap	12 dp
Floating tab bar horizontal inset	16 dp
Floating tab bar bottom offset	8 dp above navigation inset (est.)
4.3 Corner radius tokens
Component	Radius
Standard card	24 dp
Hero card	28 dp
Compact metric card	20 dp (est.)
Grouped list container	20 dp (est.)
List row inside group	12 dp (est.)
Text field	16 dp
App icon tile	14 dp (est.)
Small glyph tile	12 dp
Button	999 dp / capsule
Chip	999 dp / capsule
Segmented control outer shell	999 dp
Avatar	16 dp squircle (est.)
Bottom sheet top corners	28 dp
Icon button	16 dp (est.)
Ring	Circular
Toggle	999 dp
Focus countdown ring	Circular
Use Compose's RoundedCornerShape or CircleShape; do not create a different radius on each screen unless a component variant calls for it.

5. Elevation, bloom, grain and glass
The app should look layered without depending on expensive real-time blur. The Galaxy A32 is a mid-range device, so blur is an enhancement—not a functional dependency.
5.1 Shadow and bloom specification
Android's standard elevation shadows are often dark and subtle against black. The key visual depth here comes from thin highlights and low-alpha white bloom.
Effect	Blur radius	Spread	Colour / alpha	Use	Cheap fallback
Standard card shadow	8 dp (est.)	0 dp	Black 25% (est.)	Raised cards on dark canvas	No shadow; use border
Card top highlight	0 dp	0 dp	White 8%	Top edge of cards	1 dp top border
Card hairline	0 dp	0 dp	White 10%	Standard card outline	1 dp border
Primary CTA bloom	16 dp (est.)	0 dp	White 12%	Primary action	Two translucent rounded shapes behind button
Hero numeral bloom	18 dp (est.)	0 dp	White 25%	Large Time Bank value	Radial gradient behind text
Active tab bloom	10 dp (est.)	0 dp	White 12%	Active navigation pill	Flat white pill
Active chart/ring glow	10 dp (est.)	0 dp	White 12%	Active ring arc or today bar	Duplicate stroke at 25% alpha
Route halo	8 dp (est.)	0 dp	White 25%	Strava route line	Two Canvas strokes beneath route
Completion burst	20 dp (est.)	0 dp	White 25%	Session complete	Static radial gradient and line sparks
Focused field	8 dp (est.)	0 dp	White 12%	Focused text field	1 dp white border
Grain	N/A	N/A	White 3% (est.)	Global subtle texture	Static noise texture at low alpha
Spread is 0 dp in this design: create the softness with blur or a gradient, not a thick glowing border.
5.2 Cheap bloom fallback
When real-time blur is unavailable or slow, use a radial gradient or draw multiple translucent strokes.
Box(
    modifier = Modifier
        .size(180.dp)
        .background(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                )
            ),
            shape = CircleShape
        )
)


For a route halo, draw a wide, low-alpha white line underneath the crisp route line:
- Halo stroke: 9 dp at 25% alpha (est.).
- Main route stroke: 3 dp at 100%.
- Finish marker: 8 dp solid white (est.).
- Start marker: 10 dp ring with 2 dp white outline (est.).
This provides a similar visual impression without a live Gaussian blur.
5.3 Film-grain overlay
Property	Specification
Grain opacity	3% (est.)
Grain pixel size	1–2 px at texture source resolution (est.)
Blend	Normal / low-alpha white noise (est.)
Coverage	Full background, not individual text
Motion	Static; do not animate
Cheap fallback	Single pre-rendered, seamless noise PNG/WebP at low alpha
Use one tiled texture over the background rather than generating noise in a Compose draw loop. Avoid a large, full-resolution transparent overlay that must be regenerated on every frame.
5.4 Glass and blur
Component	Blur radius	Tint	Border	Cheap fallback
Floating tab bar	20 dp (est.)	#0B0B0C at 82% (est.)	White 10%	Solid #0B0B0C at 96%
Bottom sheet	16 dp (est.)	#1C1C1E at 94% (est.)	White 10%	Solid #1C1C1E at 98%
Modal backdrop	No blur required	Black 64% (est.)	None	Black scrim
Regular cards	No blur	#0B0B0C	White 10%	Same—no blur
Permission/dialog surfaces	No blur required	#141416	White 10%	Solid surface
Android 11 compatibility: do not assume a universal cross-window blur effect is available. Render the tab bar and sheet with a translucent solid fill by default. If you implement a platform-specific blur, test it on Android 11, 12 and 13 and fall back automatically if unsupported.

6. Icon system
6.1 Style
Property	Value
Visual language	Rounded SF Symbols-like line icons
Primary stroke	1.8 dp (est.)
Emphasised stroke	2 dp (est.)
Standard icon size	24 × 24 dp
Compact icon	16 × 16 dp
Metadata icon	18 × 18 dp (est.)
Tab icon	24 × 24 dp
Large action icon	28 × 28 dp
App glyph tile	48 × 48 dp
Large glyph tile	56 × 56 dp
Icon touch target	At least 48 × 48 dp
Standard tile radius	12 dp
App tile radius	14 dp (est.)
Use a consistent vector source, preferably Material Symbols Rounded or custom vector assets. For a true SF Symbols-inspired look, adjust stroke and proportions, not Apple's proprietary glyph files.
6.2 Active inverted tile
State	Tile	Glyph	Border / glow
Default	#1C1C1E	White 100%	White 10% border
Pressed	#242427	White 100%	White 10% border
Selected	White 100%	Black 100%	Optional white 12% bloom
Disabled	#1C1C1E at 60%	White 30%	White 5% (est.)
The inverted tile is reserved for an active selection—not for every icon in a group.

7. Component library
Dimensions are fixed in dp unless marked otherwise. Heights include internal padding. Every clickable component must provide a minimum 48 dp interaction target even if its visible glyph is smaller.
7.1 Buttons and chips
Component	Dimensions	Default	Pressed	Disabled	Selected
Primary button	Full available width × 56 dp	White fill, black label	White 85% (est.)	White 20% fill, white 40% label (est.)	N/A
Outlined button	Full available width × 48 dp	Transparent, white 70% border	White 8% fill	White 20% border, white 30% label	N/A
Compact capsule	Content width × 36 dp	#1C1C1E, white label	#242427	40% label	White fill, black label
Icon button	48 × 48 dp	#1C1C1E	#242427	30% icon	White tile, black icon
Filter chip	Content width × 40 dp	Dark fill, secondary text	#242427	30% text	White fill, black text
Subject chip	Content width × 40 dp	#1C1C1E, glyph + label	#242427	30% opacity	White fill, black glyph/text
Status pill	Content width × 28 dp (est.)	Dark fill, white/grey label	#242427	30% label	White fill if it represents active state
Watch pill	Content width × 32 dp (est.)	Dark fill, white dot + label	#242427	40% label	White dot and label at full emphasis
Button padding
- Horizontal padding: 20 dp for primary/outlined buttons.
- Icon-to-label gap: 8 dp.
- Label size: 15 sp, weight 600.
- Primary button radius: 999 dp.
- Bloom: white 12%, 16 dp blur (est.); use a gradient fallback.
Hold-to-confirm action
Property	Value
Visible circle	72 × 72 dp (est.)
Touch target	80 × 80 dp (est.)
Confirmation duration	60 seconds, as shown in the mockup
Progress	Circular white stroke
Start state	0% progress
Held state	Progress fills clockwise
Completed state	Solid white confirmation mark
Released early	Reset progress to 0% (est.)
Disabled exit button	Until hold and confirmation text are complete
UX note: a 60-second hold is unusually long for a destructive action. It is specified here because it is part of the existing mockup. Validate it with usability testing before production.
7.2 Cards and list rows
Component	Dimensions	Default	Pressed	Disabled	Selected
Standard card	Full content width; min-height by content	#0B0B0C, 24 dp radius	#141416	40% content	White 10% outline (est.)
Hero Strava card	Full content width; 28 dp radius; 220–260 dp high (est.)	Muted map, white route	#141416 overlay	40% overlay	White outline (est.)
Metric card	Full width or grid half; 20 dp radius (est.)	#0B0B0C	#141416	40% content	White outline (est.)
Agenda row	Full width; 52–60 dp high (est.)	Dark fill	#242427	40% content	White hairline + bloom
Standard list row	Full width; min-height 56 dp (est.)	Dark/inset	#242427	40% content	White 10% border (est.)
App row	Full width; min-height 60 dp (est.)	App icon + usage + control	#242427	40% content	White emphasis
Rule card	Full width; min-height 96 dp (est.)	#0B0B0C	#141416	40% content	White 10% outline
Security group	Full width; grouped rows	#141416 outer fill	#242427 row	40% content	White outline/focus
Cookie Jar entry	Full width; 72–96 dp (est.)	#0B0B0C	#141416	40% content	White star/highlight
Use a 1 dp white 10% border and 16 dp internal padding on cards. Do not force every card to a fixed height: content should wrap and scroll.
7.3 Toggles, sliders and steppers
Component	Dimensions	Default	Pressed	Disabled	Selected/on
Toggle	52 × 32 dp (est.)	Track #2A2A2D; thumb white	Thumb scales to 0.94 (est.)	Track 40%, thumb 40%	White track, black thumb (est.)
Slider track	4 dp high	#2A2A2D	Same	30% opacity	Filled segment white
Slider thumb	20 × 20 dp (est.)	White	Scale 1.08 (est.)	40% opacity	White
Numeric stepper	40 × 40 dp controls (est.)	#1C1C1E	#242427	30% opacity	N/A
Search field	48–52 dp high (est.)	#1C1C1E	White 10% outline	40% opacity	White 70% outline
Text field	52 dp high (est.)	#1C1C1E, secondary hint	White 10% outline	40% opacity	White 70% outline
Multiline text field	Min 104 dp high (est.)	#1C1C1E	White 10% outline	40% opacity	White 70% outline
For the toggle, keep the track neutral. A selected toggle is indicated by an inverted white track, not a green accent.
7.4 Segmented controls
Property	Value
Overall height	40–44 dp (est.)
Outer radius	999 dp
Outer background	#1C1C1E
Segment internal horizontal padding	12 dp (est.)
Segment label	12 sp, weight 500 (est.)
Selected segment	White fill, black label
Unselected segment	Transparent fill, white 70% label
Pressed segment	#242427
Disabled segment	White 30% label
Used for Run / Ride, Limits / Unlock rules / Blocked, and Week / Month / Year.
7.5 Time picker
Property	Value
Main numeral	56 sp (est.)
Line height	64 sp
Selected duration	45 min by default
Preset chips	40 dp high
Presets	25 / 45 / 90 / Custom
Previous/next tap target	48 × 48 dp
Selected value	White 100%
Adjacent values	White 35% (est.)
Disabled value	White 20%
Do not implement a rapidly scrolling custom wheel if a simpler accessible duration picker meets the design goal.
7.6 Rings, bars and charts
Component	Dimensions	Style
Small progress ring	32 × 32 dp	White arc, #2A2A2D track
Medium progress ring	48 × 48 dp	White arc, #2A2A2D track
Large progress ring	160 × 160 dp (est.)	White arc, subtle glow
Daily ring group	112 / 88 / 64 dp diameters (est.)	Three nested rings
Ring stroke	5–8 dp (est.)	White
Ring track	Same stroke	#2A2A2D
Compact bar	4 dp high	White fill, dark track
Standard bar	6 dp high	White fill, dark track
Weekly bar chart	6–10 dp bar width (est.)	Today white; other days white 35%
Strava route	3 dp	White 100%, 25% halo
Radar grid	1 dp	White 10%
Radar outline	1.5 dp (est.)	White 100%
Heatmap cell	10 × 10 dp (est.)	Five white opacity levels
Sparkline	1.5 dp (est.)	White 70–100%
For the radar chart, use the six axes Focus, Workout, Running, Study, Sleep and Discipline. The weakest-axis label remains secondary text, for example Weakest: Sleep.
Heatmap opacity levels
Level	White alpha
0	8% (est.)
1	20%
2	40%
3	70%
4	100%
7.7 Floating bottom navigation
Property	Value
Height	64 dp (est.)
Horizontal inset	16 dp
Width	Available width minus 32 dp
Corner radius	999 dp
Background	#0B0B0C at 82% (est.)
Border	White 10%
Blur	20 dp (est.); optional
Icon size	24 dp
Label	10 sp / 12 sp line height
Active item	White capsule/pill with black glyph (est.)
Inactive item	White 40% glyph
Bottom offset	8 dp above navigation inset (est.)
Five destinations, in this order:
1. Home
2. Focus
3. Study
4. Stats
5. Profile
Keep labels visible if the selected destination is not sufficiently obvious from the glyph alone. The Home mockup has Home active.
7.8 Bottom sheets
Property	Value
Top corner radius	28 dp
Horizontal padding	20 dp (est.)
Top padding	12–24 dp (est.)
Scrim	Black 64% (est.)
Surface tint	#1C1C1E at 94% (est.)
Border	White 10%
Drag handle	36 × 4 dp (est.)
Blur	16 dp (est.); optional
Keyboard handling	imePadding()
Maximum height	90% of available height (est.)
Scroll behaviour	Internal scroll if content exceeds max height
Use a solid sheet fill as the baseline on Android 11–13. A blurred sheet is optional enhancement.

8. Motion and easing
Animations should feel measured, restrained and physical—not playful. Use FastOutSlowInEasing for standard movement and a smooth emphasized curve for large transitions.
8.1 Timing tokens
Token	Duration	Easing	Usage
motion.instant	80 ms (est.)	Linear	Immediate feedback
motion.fast	120 ms	FastOutSlowInEasing	Small icon/colour transitions
motion.standard	220 ms	FastOutSlowInEasing	Most component state changes
motion.emphasis	320 ms	CubicBezier(0.2, 0, 0, 1) (est.)	Hero transitions
motion.enter	350 ms (est.)	CubicBezier(0.2, 0, 0, 1)	Screen/card entrance
motion.exit	250 ms (est.)	FastOutSlowInEasing	Screen/card exit
motion.celebrate	650 ms (est.)	CubicBezier(0.2, 0, 0, 1)	Session completion
motion.ringFill	600 ms (est.)	FastOutSlowInEasing	Progress ring changes
motion.barFill	400 ms (est.)	FastOutSlowInEasing	Weekly bars
motion.sheet	300 ms (est.)	CubicBezier(0.2, 0, 0, 1)	Bottom-sheet entrance
motion.buttonPress	100 ms (est.)	FastOutSlowInEasing	Press scale/surface
motion.check	250 ms (est.)	FastOutSlowInEasing	Completion check draw
motion.bloomPulse	1800 ms per cycle (est.)	FastOutSlowInEasing	Subtle active glow
motion.countdown	1000 ms per second	Linear	Timer display
motion.holdConfirm	60000 ms	Linear	Hold-to-confirm progress
8.2 Animation behaviour by component
Element	Behaviour
Button press	Scale to 0.98 over 100 ms (est.); return over 120 ms (est.)
Primary CTA bloom	Opacity 12% → 18% (est.) over 180 ms (est.); no constant looping
Tab selection	White active pill moves over 220 ms
Segmented control	Selected capsule moves over 220 ms
Ring fill	Animate from prior value to new value over 600 ms
Countdown	Update displayed value once per second; use tabular numerals
Bloom pulse	Opacity 12% → 18% → 12% over 1800 ms (est.); only on key hero elements
Bottom sheet	Slide up over 300 ms; fade scrim over 220 ms (est.)
Sheet dismissal	Slide down over 250 ms
Check mark	Draw/scale over 250 ms (est.)
Completion burst	Expand/fade over 650 ms (est.)
List selection	Surface/border change over 120 ms
Toggle	Thumb translation over 180 ms (est.)
Slider	Thumb tracks gesture directly; animate released value only if needed
Chart bars	Grow from baseline over 400 ms (est.)
Strava route	Draw-on animation over 600 ms (est.); skip on every recomposition
Grain	Static texture; no animation
Use animate*AsState, updateTransition and Animatable selectively. Respect system animator scale and avoid motion that blocks a critical action.

9. Full navigation map
The design references use labels such as A1, A2 and A4–A17. A3 and A16 were not defined in the supplied screen descriptions, so they should not be invented as completed screens. The active Focus session is described below as a required flow state but was not assigned a numbered screen.
9.1 Navigation diagram
#chatgpt-mermaid-_r_1sj_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-_r_1sj_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-_r_1sj_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-_r_1sj_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-_r_1sj_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-_r_1sj_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-_r_1sj_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-_r_1sj_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-_r_1sj_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-_r_1sj_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_1sj_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_1sj_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-_r_1sj_ p{margin:0;}#chatgpt-mermaid-_r_1sj_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-_r_1sj_ .label text,#chatgpt-mermaid-_r_1sj_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ .node rect,#chatgpt-mermaid-_r_1sj_ .node circle,#chatgpt-mermaid-_r_1sj_ .node ellipse,#chatgpt-mermaid-_r_1sj_ .node polygon,#chatgpt-mermaid-_r_1sj_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .rough-node .label text,#chatgpt-mermaid-_r_1sj_ .node .label text,#chatgpt-mermaid-_r_1sj_ .image-shape .label,#chatgpt-mermaid-_r_1sj_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-_r_1sj_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .rough-node .label,#chatgpt-mermaid-_r_1sj_ .node .label,#chatgpt-mermaid-_r_1sj_ .image-shape .label,#chatgpt-mermaid-_r_1sj_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-_r_1sj_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-_r_1sj_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_1sj_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-_r_1sj_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-_r_1sj_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-_r_1sj_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-_r_1sj_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-_r_1sj_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-_r_1sj_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-_r_1sj_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-_r_1sj_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-_r_1sj_ .icon-shape,#chatgpt-mermaid-_r_1sj_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-_r_1sj_ .icon-shape p,#chatgpt-mermaid-_r_1sj_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-_r_1sj_ .icon-shape .label rect,#chatgpt-mermaid-_r_1sj_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-_r_1sj_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-_r_1sj_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-_r_1sj_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node rect,#chatgpt-mermaid-_r_1sj_ [data-look="neo"].cluster rect,#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-_r_1sj_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-_r_1sj_-gradient);stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-_r_1sj_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-_r_1sj_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_1sj_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-_r_1sj_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_1sj_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-_r_1sj_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-_r_1sj_ .node tspan[font-weight="normal"],#chatgpt-mermaid-_r_1sj_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-_r_1sj_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .node rect,#chatgpt-mermaid-_r_1sj_ .node circle,#chatgpt-mermaid-_r_1sj_ .node ellipse,#chatgpt-mermaid-_r_1sj_ .node polygon,#chatgpt-mermaid-_r_1sj_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-_r_1sj_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-_r_1sj_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-_r_1sj_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-_r_1sj_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-_r_1sj_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Home — 1A / 1BFocus tabStudy tabStats — A14Profile — A15Permissions — A1Start Focus Session — A2Active Focus SessionLeave Early sheet — A4Session Complete — A5App Rules — A6New Rule — A7Allowed Callers — A8Allowed Apps — A9Time Bank — A10Accountability Mirror — A11Cookie Jar — A12Rank — A13Security — A17





9.2 Screen-by-screen entry points
Screen	Entry point	Exit / next screen
Home 1A	App launch; Home tab	Scroll to 1B; tap Strava hero, balance, Time Bank, agenda, rings or navigation
Home 1B	Scroll down from Home 1A	Tap agenda item, Time Bank, workout, streak/rank, or weekly chart
A1 — Setup lock	First launch or missing required permissions	Grant permissions → A2; Continue disabled until required permissions are granted (est.)
A2 — Start Focus Session	Focus tab, Study action or agenda Focus task	Begin Focus → Active Focus Session
Active Focus Session	Begin Focus in A2	Complete → A5; Leave Early → A4
A4 — Leave Early	Leave Early action in active session	Stay focused → Active session; confirm exit → end session and return to Home (est.)
A5 — Session Complete	Timer reaches zero or session completes	Log questions → Study/log flow; Done → Home (est.)
A6 — App Rules	Profile/Settings or Time Bank	Add rule → A7; manage Limits, Unlock rules or Blocked
A7 — New Rule	+ New rule from A6 or Time Bank	Save → A6; cancel/back → previous screen
A8 — Allowed Callers	Profile/Connections or A2's Allowed callers → Edit	Add number/import/edit → A8; back → caller selection or Profile
A9 — Allowed Apps	A2's Allowed apps → Edit, or Profile settings	Select allowed apps → save/back to A2 or Profile
A10 — Time Bank	Home card, tab-adjacent action, or unlock action	Unlock apps; Earn more → Focus, Workout, Study or Rules
A11 — Accountability Mirror	Home agenda at 9 PM, notification or reminder	Face it → save reflection and return Home
A12 — Cookie Jar	Profile, Rank or Stats	Add entry → entry creation; back → originating screen
A13 — Rank	Home streak/XP strip, Profile or Stats	View targets; back → originating screen
A14 — Stats	Stats tab	Workout progress, Study stats, Rank or Cookie Jar
A15 — Profile	Profile tab	Open connections, Watch, Permissions, Security, notifications, units, export or About
A17 — Security	Profile → Security	Back → Profile
A3 — Undefined	Not specified	Do not implement as a known design screen until its purpose is supplied
A16 — Undefined	Not specified	Do not implement as a known design screen until its purpose is supplied
Navigation details to preserve
- The bottom tab bar contains Home, Focus, Study, Stats and Profile.
- Home 1A and 1B are two scroll positions of one screen, not two separate navigation destinations.
- A4 is a bottom sheet over the active session, not a separate full-screen destination.
- A7 is a rule-creation screen or sheet launched from A6; the final presentation should match the implementation choice.
- A1 appears during onboarding or whenever the app requires permissions to operate. Do not force users through onboarding on every launch.
- The Focus timer must retain its session state if the user navigates to permission/settings screens or the app is temporarily backgrounded.

10. Screen-specific implementation checklist
Use this as the final UI coverage checklist. It ties the shared components back to every screen in the handoff.
Screen	Required content and distinctive treatment
Home 1A	Header, Watch pill, Strava route hero, Run/Ride segmented control, six-axis balance radar, screen-time summary, floating tabs
Home 1B	Agenda, current-task highlight, Time Bank, study metrics and subject tiles, daily rings, streak/XP, workout cards, weekly focus bars
A1 Setup	Five permission rows, granted state icons, Allow controls, progress count, Continue button, no bottom tabs
A2 Start Focus	Duration picker, session name field, subject chips, presets, allowed-app list, allowed callers, strict-lock toggle, Begin Focus
Active Focus	Large timer, session name/subject, strict-lock state, Leave Early action (layout details still require visual definition)
A4 Leave Early	40% progress, warning text, 60-second hold ring, exact confirmation phrase, disabled Exit session, Stay focused
A5 Complete	Glowing check, session duration, subject, focus/streak/XP stats, earned Time Bank minutes, Log questions, Done
A6 App Rules	Limits/Unlock rules/Blocked segments, per-app usage, sliders, default locks, verified activity rule cards, New Rule
A7 New Rule	IF/THEN builder, condition and target selectors, verified-source note, reset setting, Save rule
A8 Allowed Callers	Search, contact rows, monograms, number/toggle, import contacts, Add number, blocked-call statistic
A9 Allowed Apps	Allowed app tile grid, selected inverted tiles, search, selectable app rows, Settings always blocked note
A10 Time Bank	Earned minutes, earned/spent bar, app unlock actions, progress rings for earning tasks, midnight reset
A11 Accountability Mirror	Daily agenda completion states, missed-item penalty, reflection field, Face it CTA
A12 Cookie Jar	Past difficult achievements, date/title cards, star glyph, Add entry CTA
A13 Rank	Current rank, progress to next rank, vertical rank ladder, weekly target progress, 5% target-increase note
A14 Stats	Week/Month/Year, focus hours, distance line, screen-time donut, workout load, streak heatmap, Study/Workout detail rows
A15 Profile	Avatar, name, rank, streak, grouped settings, connection status, Watch toggle, permission list, security, notification tone, units, export, About, danger zone
A17 Security	Biometric lock, lock on exit, hide in recents, encrypted data always-on state, auto-lock duration, secure hardware note, shield illustration

11. Suggested Compose implementation structure
com.focus.app
├── MainActivity.kt
├── navigation/
│   ├── FocusNavHost.kt
│   ├── FocusRoute.kt
│   └── BottomNavigation.kt
├── design/
│   ├── color/FocusColors.kt
│   ├── typography/FocusTypography.kt
│   ├── shape/FocusShapes.kt
│   ├── motion/FocusMotion.kt
│   └── component/
│       ├── FocusButton.kt
│       ├── FocusCard.kt
│       ├── FocusPill.kt
│       ├── FocusToggle.kt
│       ├── FocusSlider.kt
│       ├── FocusSegmentedControl.kt
│       ├── FocusRing.kt
│       ├── FocusBottomBar.kt
│       ├── FocusBottomSheet.kt
│       └── FocusTextField.kt
├── feature/
│   ├── home/
│   ├── onboarding/
│   ├── focus/
│   ├── study/
│   ├── rules/
│   ├── callers/
│   ├── allowedapps/
│   ├── timebank/
│   ├── accountability/
│   ├── cookiejar/
│   ├── rank/
│   ├── stats/
│   ├── profile/
│   └── security/
└── data/
    ├── repository/
    ├── model/
    └── persistence/


Final acceptance criteria
- [ ] At 411 dp width, the screen uses 16 dp gutters and cards expand to 379 dp.
- [ ] No accent colours appear in the UI.
- [ ] Inter is packaged as the production font; SF Pro remains a visual reference.
- [ ] Text and controls respond to accessibility font scaling.
- [ ] All clickable targets meet the 48 × 48 dp minimum.
- [ ] Status, navigation and keyboard insets are applied once per edge.
- [ ] All bloom effects have a non-blurred fallback.
- [ ] Film grain is a static texture, not a continuously generated animation.
- [ ] The tab bar and bottom sheets remain legible without platform blur.
- [ ] Session timer state survives recomposition and temporary backgrounding.
- [ ] Reduced-motion settings are respected.
- [ ] A3 and A16 remain unspecified until their intended screens are defined.
Engineering recommendation: implement the solid monochrome surfaces, typography, spacing and component states first. Add radial-gradient bloom second. Treat real-time glass blur and grain as optional visual enhancements so the app retains the intended premium look and responsive interaction on the Galaxy A32 across Android 11–13.