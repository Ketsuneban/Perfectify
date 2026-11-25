using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Automation.Text;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Threading;
using System.IO;
using System.Diagnostics;

namespace NoteSync;

internal static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        if (args.Contains("--self-test"))
        {
            if (ListMap.Parse("1. A\n1. B\n1. C", "Auto")[2] != 3 ||
                ListMap.Parse("1. A\n5. B", "Literal")[1] != 5 ||
                ListMap.Parse("4. A\n9. B", "Markdown")[1] != 5 ||
                ListMap.Parse("title\r\n1. A\r\n2. B", "Auto")[2] != 2 ||
                ListMap.Parse("1. A\n\n1. B\n1.", "Auto")[3] != 3 ||
                ListMap.Parse("1. A\n\n3. C", "Auto")[2] != 3 ||
                ListMap.Parse("1.\n\n3. C", "Literal")[0] != 1 ||
                ListMap.Parse("", "Auto")[0] != null ||
                Native.FileName("A.txt - Notepad") != "A" ||
                ListMap.Current("1. A\n2. B\n", 2, "Markdown", true) != 3 ||
                ListMap.Current("1. A\n1. B\n", 2, "Auto", true) != 3 ||
                ListMap.Current("1. A\n\n3. C", 1, "Literal", true) != null ||
                ListMap.Current("1. A\n\n\n", 2, "Markdown", true) != null ||
                ListMap.Current("1. A\n2.", 1, "Auto", true) != 2)
                Environment.Exit(1);
            return;
        }
        new Application().Run(new MainWindow());
    }
}

internal static class ListMap
{
    static readonly Regex Marker = new(@"^\s*(\d{1,9})[.)](?:\s+|$)", RegexOptions.Compiled);
    public static string[] Lines(string text) =>
        text.Replace("\r\n", "\n").Replace('\r', '\n').Split('\n');
    public static int LineOf(string text) => Lines(text).Length - 1;
    public static int? Current(string text, int line, string mode, bool allowNewBlank)
    {
        var lines = Lines(text);
        var map = Parse(text, mode);
        if (line < 0 || line >= map.Length) return null;
        if (map[line].HasValue) return map[line];
        if (allowNewBlank && mode != "Literal" && line > 0 && string.IsNullOrWhiteSpace(lines[line]) && map[line - 1] is int previous)
            return previous + 1;
        return null;
    }
    public static int?[] Parse(string text, string mode)
    {
        var lines = Lines(text);
        var result = new int?[lines.Length];
        for (int i = 0; i < lines.Length;)
        {
            var m = Marker.Match(lines[i]);
            if (!m.Success) { i++; continue; }
            var positions = new List<int>();
            var numbers = new List<int>();
            while (i < lines.Length)
            {
                m = Marker.Match(lines[i]);
                if (m.Success) { positions.Add(i); numbers.Add(int.Parse(m.Groups[1].Value)); }
                else if (!string.IsNullOrWhiteSpace(lines[i])) break;
                i++;
            }
            bool sequential = mode == "Markdown" ||
                (mode == "Auto" && numbers.Count > 1 && numbers.Distinct().Count() < numbers.Count);
            for (int j = 0; j < numbers.Count; j++)
                result[positions[j]] = sequential ? numbers[0] + j : numbers[j];
        }
        return result;
    }
}

internal sealed class MainWindow : Window
{
    readonly TextBox titleFilter = new() { Margin = new Thickness(0, 5, 0, 8) };
    readonly TextBox secondFilter = new() { Margin = new Thickness(0, 5, 0, 12) };
    readonly FloatingToggle floating;
    readonly DispatcherTimer floatTimer;
    nint lastNote;
    readonly ComboBox mode = new() { Margin = new Thickness(0, 5, 0, 12), ItemsSource = new[] { "Auto", "Literal", "Markdown" }, SelectedIndex = 0 };
    readonly TextBlock status = new() { Text = "Остановлено", TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 12, 0, 0) };
    readonly Button toggle = new() { Content = "Запустить", Height = 36 };
    readonly CheckBox scrollOnly = new() { Content = "Только прокрутка (без выделения)", Margin = new Thickness(0, 0, 0, 12) };
    CancellationTokenSource? session;
    public MainWindow()
    {
        Title = "NoteSync";
        Width = 470; Height = 435; MinWidth = 430; MinHeight = 435;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        var panel = new StackPanel { Margin = new Thickness(22) };
        panel.Children.Add(new TextBlock { Text = "Синхронизация двух заметок", FontSize = 21, FontWeight = FontWeights.SemiBold });
        panel.Children.Add(new TextBlock { Text = "Имя первого файла (без расширения)", Margin = new Thickness(0, 14, 0, 0) });
        panel.Children.Add(titleFilter);
        panel.Children.Add(new TextBlock { Text = "Имя второго файла (можно такое же)" });
        panel.Children.Add(secondFilter);
        panel.Children.Add(new TextBlock { Text = "Нумерация: Auto / Literal / Markdown" });
        panel.Children.Add(mode);
        panel.Children.Add(scrollOnly);
        panel.Children.Add(toggle);
        panel.Children.Add(status);
        Content = panel;
        toggle.Click += (_, _) => Toggle();
        floating = new FloatingToggle(() => Toggle());
        floatTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(350) };
        floatTimer.Tick += (_, _) => RefreshFloating();
        floatTimer.Start();
        Closed += (_, _) => { floatTimer.Stop(); floating.Close(); session?.Cancel(); };
    }
    void RefreshFloating()
    {
        var active = Native.GetForegroundWindow();
        if (Native.IsNotepad(active) && !Native.IsIconic(active)) lastNote = active;
        else lastNote = 0;
        if (lastNote == 0 || !Native.IsNotepad(lastNote) || Native.IsIconic(lastNote) ||
            !Native.GetWindowRect(lastNote, out var bounds) || bounds.Right - bounds.Left < 280)
        { floating.Hide(); return; }
        floating.Update(session != null, bounds);
    }
    void Toggle()
    {
        if (session != null)
        {
            session.Cancel(); session = null;
            toggle.Content = "Запустить"; status.Text = "Остановлено";
            titleFilter.IsEnabled = secondFilter.IsEnabled = mode.IsEnabled = scrollOnly.IsEnabled = true;
            return;
        }
        var filter = Native.InputName(titleFilter.Text);
        var second = Native.InputName(secondFilter.Text);
        if (filter.Length == 0 || second.Length == 0)
        {
            WindowState = WindowState.Normal; Show(); Activate();
            status.Text = "Введи имена обоих файлов.";
            return;
        }
        var current = new CancellationTokenSource();
        session = current;
        var selectedMode = (string)mode.SelectedItem;
        bool onlyScroll = scrollOnly.IsChecked == true;
        toggle.Content = "Остановить";
        titleFilter.IsEnabled = secondFilter.IsEnabled = mode.IsEnabled = scrollOnly.IsEnabled = false;
        status.Text = "Открой указанные файлы в отдельных окнах Блокнота и поставь курсор в текст.";
        var thread = new Thread(() =>
        {
            try
            {
                new Synchronizer(filter, second, selectedMode, onlyScroll, message =>
                {
                    if (!current.IsCancellationRequested)
                        Dispatcher.BeginInvoke(new Action(() =>
                        {
                            if (session == current) status.Text = message;
                        }));
                }).Run(current.Token);
            }
            finally { Dispatcher.BeginInvoke(new Action(() => current.Dispose())); }
        }) { IsBackground = true };
        thread.SetApartmentState(ApartmentState.MTA);
        thread.Start();
        WindowState = WindowState.Minimized;
    }
}

internal sealed class Synchronizer(string filter, string second, string mode, bool scrollOnly, Action<string> report)
{
    (nint from, nint to, int number, string text)? last;
    nint activeSource, activeTarget;
    string activeTitle = "", targetTitle = "";
    AutomationElement? sourceEditor, destEditor;
    string? sourceText, destText;
    int sourceChanged = 1, targetChanged = 1, selectionChanged = 1;
    long nextDiscovery, nextFallback, nextCheck;
    uint lastInput;
    int previousLine = -1;
    int pendingBlank = -1;
    AutomationEventHandler? onSelection, onSourceText, onTargetText;
    string previousMessage = "";
    void Status(string message)
    {
        if (message == previousMessage) return;
        previousMessage = message; report(message);
    }
    public void Run(CancellationToken token)
    {
        try
        {
            while (!token.IsCancellationRequested)
            {
                try { Tick(token); }
                catch (Exception e) when (e is not OutOfMemoryException)
                {
                    Reset();
                    nextDiscovery = Environment.TickCount64 + 1000;
                    Status("UI Automation: " + e.Message);
                }
                token.WaitHandle.WaitOne(120);
            }
        }
        finally { Reset(); }
    }
    void Reset()
    {
        Remove(sourceEditor, TextPattern.TextSelectionChangedEvent, onSelection);
        Remove(sourceEditor, TextPattern.TextChangedEvent, onSourceText);
        Remove(destEditor, TextPattern.TextChangedEvent, onTargetText);
        sourceEditor = destEditor = null;
        onSelection = onSourceText = onTargetText = null;
        activeSource = activeTarget = 0;
        activeTitle = targetTitle = "";
        sourceText = destText = null;
        last = null; previousLine = pendingBlank = -1;
        Interlocked.Exchange(ref sourceChanged, 1);
        Interlocked.Exchange(ref targetChanged, 1);
        Interlocked.Exchange(ref selectionChanged, 1);
    }
    static void Remove(AutomationElement? element, AutomationEvent evt, AutomationEventHandler? handler)
    {
        if (element == null || handler == null) return;
        try { Automation.RemoveAutomationEventHandler(evt, element, handler); } catch { }
    }
    static void Subscribe(AutomationElement element, AutomationEvent evt, AutomationEventHandler handler)
    {
        try { Automation.AddAutomationEventHandler(evt, element, TreeScope.Element, handler); }
        catch { /* Input-triggered fallback for providers that do not raise events. */ }
    }
    void Tick(CancellationToken token)
    {
        long now = Environment.TickCount64;
        var source = Native.GetForegroundWindow();
        if (source != activeSource && !Native.IsNotepad(source))
        {
            if (activeSource != 0) Reset();
            return;
        }
        var title = Native.FileName(Native.Title(source));
        bool first = string.Equals(title, filter, StringComparison.OrdinalIgnoreCase);
        bool other = string.Equals(title, second, StringComparison.OrdinalIgnoreCase);
        if (!first && !other) { if (activeSource != 0) Reset(); return; }
        var wanted = first ? second : filter;
        bool changedWindow = source != activeSource || title != activeTitle;
        if (changedWindow && activeSource != 0) { Reset(); nextDiscovery = 0; }
        if (activeTarget != 0 && (!Native.IsWindow(activeTarget) ||
            !string.Equals(Native.FileName(Native.Title(activeTarget)), wanted, StringComparison.OrdinalIgnoreCase)))
        { Reset(); nextDiscovery = 0; }
        if (now >= nextDiscovery)
        {
            nextDiscovery = now + 1800;
            var targets = Native.Windows().Where(w => w != source &&
                string.Equals(Native.FileName(Native.Title(w)), wanted, StringComparison.OrdinalIgnoreCase) &&
                Native.IsNotepad(w)).ToArray();
            if (targets.Length != 1)
            {
                if (activeSource != 0) Reset();
                Status(targets.Length == 0 ? "Второй файл не открыт." : "Найдено несколько подходящих окон.");
                return;
            }
            if (activeTarget != 0 && activeTarget != targets[0]) Reset();
            activeSource = source; activeTarget = targets[0]; activeTitle = title;
            targetTitle = Native.Title(activeTarget);
        }
        if (activeSource != source || activeTarget == 0 || Native.IsIconic(activeTarget)) return;
        var target = activeTarget;
        var currentTargetTitle = Native.Title(target);
        if (currentTargetTitle != targetTitle)
        {
            targetTitle = currentTargetTitle;
            Interlocked.Exchange(ref targetChanged, 1);
        }
        uint input = Native.LastInput();
        bool fallback = input != lastInput && now >= nextFallback;
        bool dirtySource = Interlocked.Exchange(ref sourceChanged, 0) != 0;
        bool dirtyTarget = Interlocked.Exchange(ref targetChanged, 0) != 0;
        bool dirtySelection = Interlocked.Exchange(ref selectionChanged, 0) != 0;
        if (fallback) { lastInput = input; nextFallback = now + 650; }
        if (!fallback && !dirtySource && !dirtyTarget && !dirtySelection && now < nextCheck) return;
        // No periodic UIA document reads while idle. The health check only
        // retries discovery if a provider was unavailable.
        if (sourceEditor != null && destEditor != null && !fallback &&
            !dirtySource && !dirtyTarget && !dirtySelection) return;
        nextCheck = now + 1200;
        if (sourceEditor == null || fallback)
        {
            var root = AutomationElement.FromHandle(source);
            var focused = FindFocused(AutomationElement.FocusedElement, root);
            if (focused == null)
            {
                Status("Поставь курсор в текст заметки.");
                return;
            }
            if (sourceEditor == null || !sourceEditor.Equals(focused))
            {
                Remove(sourceEditor, TextPattern.TextSelectionChangedEvent, onSelection);
                Remove(sourceEditor, TextPattern.TextChangedEvent, onSourceText);
                sourceEditor = focused; sourceText = null; pendingBlank = previousLine = -1; last = null;
                onSelection = (_, _) => Interlocked.Exchange(ref selectionChanged, 1);
                onSourceText = (_, _) => { Interlocked.Exchange(ref sourceChanged, 1); Interlocked.Exchange(ref selectionChanged, 1); };
                Subscribe(sourceEditor, TextPattern.TextSelectionChangedEvent, onSelection);
                Subscribe(sourceEditor, TextPattern.TextChangedEvent, onSourceText);
            }
        }
        if (sourceEditor == null || !TryText(sourceEditor, out var src) || src == null) return;
        var selections = src.GetSelection();
        if (selections.Length != 1) return;
        var caret = selections[0].Clone();
        caret.MoveEndpointByRange(TextPatternRangeEndpoint.End, caret, TextPatternRangeEndpoint.Start);
        var prefix = src.DocumentRange.Clone();
        prefix.MoveEndpointByRange(TextPatternRangeEndpoint.End, caret, TextPatternRangeEndpoint.Start);
        string before = prefix.GetText(-1);
        int line = ListMap.LineOf(before);
        string? oldText = sourceText;
        if (sourceText == null || dirtySource || fallback) sourceText = src.DocumentRange.GetText(-1);
        var lines = ListMap.Lines(sourceText);
        if (line >= lines.Length) { Interlocked.Exchange(ref sourceChanged, 1); return; }
        // A newly inserted blank paragraph directly after an ordered item is
        // the next Markdown item even before the provider exposes its marker.
        if (oldText != null && sourceText != oldText && line > 0 && (line == previousLine + 1 || line == previousLine) &&
            lines.Length > ListMap.Lines(oldText).Length && string.IsNullOrWhiteSpace(lines[line]))
            pendingBlank = line;
        else if (line != pendingBlank || !string.IsNullOrWhiteSpace(lines[line])) pendingBlank = -1;
        previousLine = line;
        int? number = ListMap.Current(sourceText, line, mode, mode == "Markdown" || pendingBlank == line);
        if (number == null && (!string.IsNullOrWhiteSpace(lines[line]) || pendingBlank == line || mode == "Markdown"))
            number = ItemNumber(sourceEditor, src, caret);
        if (number == null)
        {
            last = null;
            Status("Пустая или ненумерованная строка.");
            return;
        }
        if (destEditor == null)
        {
            destEditor = TargetEditor(target);
            if (destEditor == null) { Status("Во втором окне нет доступного редактора."); return; }
            onTargetText = (_, _) => Interlocked.Exchange(ref targetChanged, 1);
            Subscribe(destEditor, TextPattern.TextChangedEvent, onTargetText);
            destText = null;
        }
        if (!dirtyTarget && destText != null && last is { } previous && previous.from == source && previous.to == target && previous.number == number.Value) return;
        if (!TryText(destEditor, out var dst) || dst == null) return;
        if (destText == null || dirtyTarget || last == null || last.Value.number != number.Value)
            destText = dst.DocumentRange.GetText(-1);
        var key = (source, target, number.Value, destText);
        if (last == key) return;
        TextPatternRange? range = null;
        var matches = ListMap.Parse(destText, mode).Select((n, i) => (n, i))
            .Where(x => x.n == number).ToArray();
        if (matches.Length == 1) range = FindLine(dst, destText, matches[0].i);
        else if (matches.Length == 0)
        {
            var match = Items(destEditor, dst).Where(x => x.number == number).ToArray();
            if (match.Length == 1) range = match[0].range;
        }
        if (range == null)
        {
            last = key;
            Status("Соответствующий пункт не найден однозначно.");
            return;
        }
        if (token.IsCancellationRequested || Native.GetForegroundWindow() != source) return;
        if (!scrollOnly && dst.SupportedTextSelection != SupportedTextSelection.None) range.Select();
        if (Native.GetForegroundWindow() == source)
            CenterRange(destEditor, dst, range, token, source);
        if (Native.GetForegroundWindow() != source)
        {
            last = null;
            if (Native.GetForegroundWindow() == target)
            {
                Status("Блокнот изменил фокус; синхронизация приостановлена. Перезапусти или включи только прокрутку.");
                token.WaitHandle.WaitOne();
            }
            else Status("Фокус изменился; синхронизация продолжится при следующем переходе.");
            return;
        }
        last = key;
        Status($"Пункт {number} — синхронизирован");
    }
    static void CenterRange(AutomationElement editor, TextPattern pattern, TextPatternRange range, CancellationToken token, nint source)
    {
        range.ScrollIntoView(true);
        if (token.IsCancellationRequested || Native.GetForegroundWindow() != source) return;
        var viewport = editor.Current.BoundingRectangle;
        if (viewport.IsEmpty || viewport.Height <= 0) return;
        ScrollPattern? scroll = null;
        AutomationElement? parent = editor;
        for (int i = 0; i < 6 && parent != null; i++)
        {
            if (parent.TryGetCurrentPattern(ScrollPattern.Pattern, out var obj) && obj is ScrollPattern candidate &&
                candidate.Current.VerticallyScrollable)
            { scroll = candidate; break; }
            parent = TreeWalker.RawViewWalker.GetParent(parent);
        }
        if (scroll != null)
        {
            for (int i = 0; i < 2; i++)
            {
                if (token.IsCancellationRequested || Native.GetForegroundWindow() != source) return;
                var rects = range.GetBoundingRectangles();
                if (rects.Length == 0) break;
                double delta = rects[0].Top + rects[0].Height / 2 - (viewport.Top + viewport.Height / 2);
                if (Math.Abs(delta) < 14) return;
                double view = scroll.Current.VerticalViewSize;
                double old = scroll.Current.VerticalScrollPercent;
                if (view <= 0 || view >= 100 || old < 0) break;
                double next = Math.Clamp(old + delta / viewport.Height * view / (100 - view) * 100, 0, 100);
                if (Math.Abs(next - old) < 0.05) return;
                scroll.SetScrollPercent(ScrollPattern.NoScroll, next);
            }
            return;
        }
        // TextPattern-only fallback: align a point half a viewport before
        // the selected visual line with the top of the editor.
        var bounds = range.GetBoundingRectangles();
        if (bounds.Length == 0 || bounds[0].Height <= 0) return;
        int half = Math.Clamp((int)(viewport.Height / bounds[0].Height / 2), 1, 100);
        var anchor = range.Clone();
        anchor.MoveEndpointByRange(TextPatternRangeEndpoint.End, anchor, TextPatternRangeEndpoint.Start);
        anchor.Move(TextUnit.Line, -half);
        if (!token.IsCancellationRequested && Native.GetForegroundWindow() == source) anchor.ScrollIntoView(true);
    }
    static bool TryText(AutomationElement element, out TextPattern? text)
    {
        text = null;
        if (!element.TryGetCurrentPattern(TextPattern.Pattern, out var value)) return false;
        text = value as TextPattern;
        return text != null;
    }
    static AutomationElement? FindFocused(AutomationElement? current, AutomationElement root)
    {
        AutomationElement? candidate = null;
        for (int i = 0; current != null && i < 40; i++)
        {
            if (current.Equals(root)) return candidate;
            if (candidate == null && TryText(current, out _)) candidate = current;
            current = TreeWalker.RawViewWalker.GetParent(current);
        }
        return null;
    }
    AutomationElement? TargetEditor(nint hwnd)
    {
        // Re-discover visible editor each tick: active Notepad tab may have changed.
        var root = AutomationElement.FromHandle(hwnd);
        var condition = new OrCondition(new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Document),
            new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Edit));
        var candidates = root.FindAll(TreeScope.Descendants, condition).Cast<AutomationElement>()
            .Where(e => !e.Current.IsOffscreen && TryText(e, out _))
            .OrderByDescending(e => e.Current.BoundingRectangle.Width * e.Current.BoundingRectangle.Height).ToArray();
        var found = candidates.FirstOrDefault();
        return found;
    }
    static TextPatternRange? FindLine(TextPattern pattern, string doc, int line)
    {
        var lines = ListMap.Lines(doc);
        if (line < 0 || line >= lines.Length) return null;
        var value = lines[line];
        if (value.Length == 0) return null;
        var search = pattern.DocumentRange.Clone();
        for (int i = 0; i < 20000; i++)
        {
            var match = search.FindText(value, false, false);
            if (match == null) return null;
            var prefix = pattern.DocumentRange.Clone();
            prefix.MoveEndpointByRange(TextPatternRangeEndpoint.End, match, TextPatternRangeEndpoint.Start);
            var before = prefix.GetText(-1);
            int foundLine = ListMap.LineOf(before);
            if (foundLine == line && (before.Length == 0 || before.EndsWith("\n") || before.EndsWith("\r"))) return match;
            if (foundLine > line) return null;
            search.MoveEndpointByRange(TextPatternRangeEndpoint.Start, match, TextPatternRangeEndpoint.End);
        }
        return null;
    }
    static List<(int number, TextPatternRange range)> Items(AutomationElement editor, TextPattern pattern)
    {
        var result = new List<(int, TextPatternRange)>();
        var items = editor.FindAll(TreeScope.Descendants,
            new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.ListItem));
        int i = 0;
        foreach (AutomationElement item in items)
        {
            i++;
            try
            {
                var r = pattern.RangeFromChild(item);
                if (r != null)
                {
                    var match = Regex.Match(r.GetText(-1).TrimStart(), @"^(\d{1,9})[.)](?:\s|$)");
                    result.Add((match.Success ? int.Parse(match.Groups[1].Value) : i, r));
                }
            }
            catch (ArgumentException) { }
            catch (InvalidOperationException) { }
        }
        return result;
    }
    static int? ItemNumber(AutomationElement editor, TextPattern pattern, TextPatternRange caret)
    {
        foreach (var (number, range) in Items(editor, pattern))
        {
            if (range.CompareEndpoints(TextPatternRangeEndpoint.Start, caret, TextPatternRangeEndpoint.Start) <= 0 &&
                (range.CompareEndpoints(TextPatternRangeEndpoint.End, caret, TextPatternRangeEndpoint.Start) > 0 ||
                 range.CompareEndpoints(TextPatternRangeEndpoint.Start, caret, TextPatternRangeEndpoint.Start) == 0))
                return number;
        }
        return null;
    }
}

internal static class Native
{
    delegate bool EnumProc(nint hwnd, nint param);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, nint param);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(nint hwnd);
    [DllImport("user32.dll")] public static extern bool IsWindow(nint hwnd);
    [StructLayout(LayoutKind.Sequential)]
    struct LastInputInfo { public uint Size, Time; }
    [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LastInputInfo info);
    public static uint LastInput()
    {
        var info = new LastInputInfo { Size = (uint)Marshal.SizeOf<LastInputInfo>() };
        return GetLastInputInfo(ref info) ? info.Time : 0;
    }
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(nint hwnd, System.Text.StringBuilder text, int max);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(nint hwnd, out uint pid);
    [DllImport("user32.dll")] public static extern nint GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool IsIconic(nint hwnd);
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(nint hwnd, out Rect rect);
    [DllImport("user32.dll", EntryPoint = "GetWindowLongW")] public static extern int GetWindowLong(nint hwnd, int index);
    [DllImport("user32.dll", EntryPoint = "SetWindowLongW")] public static extern int SetWindowLong(nint hwnd, int index, int value);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(nint hwnd, nint after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern uint GetDpiForWindow(nint hwnd);
    static readonly System.Collections.Concurrent.ConcurrentDictionary<uint, (bool isNotepad, long expires)> processTypes = new();
    public static bool IsNotepad(nint hwnd)
    {
        if (hwnd == 0) return false;
        GetWindowThreadProcessId(hwnd, out uint pid);
        long now = Environment.TickCount64;
        if (processTypes.TryGetValue(pid, out var cached) && cached.expires > now) return cached.isNotepad;
        try
        {
            using var p = Process.GetProcessById((int)pid);
            bool result = p.ProcessName.Equals("notepad", StringComparison.OrdinalIgnoreCase);
            processTypes[pid] = (result, now + 3000);
            return result;
        }
        catch { return false; }
    }
    public static string InputName(string input)
    {
        var name = Path.GetFileName(input.Trim().Trim('"'));
        // Only known text extensions; don't strip arbitrary dots in a note name.
        if (name.EndsWith(".txt", StringComparison.OrdinalIgnoreCase) || name.EndsWith(".md", StringComparison.OrdinalIgnoreCase) ||
            name.EndsWith(".markdown", StringComparison.OrdinalIgnoreCase))
            return Path.GetFileNameWithoutExtension(name);
        return name;
    }
    public static string FileName(string title)
    {
        title = Regex.Replace(title, @"\s+[-–—]\s+(Notepad|Блокнот)\s*$", "", RegexOptions.IgnoreCase);
        return InputName(title.Trim().Trim('*', '●', '•', ' '));
    }
    public static bool Own(nint hwnd)
    {
        GetWindowThreadProcessId(hwnd, out uint pid);
        return pid == Environment.ProcessId;
    }
    public static string Title(nint hwnd)
    {
        var text = new System.Text.StringBuilder(2048);
        GetWindowText(hwnd, text, text.Capacity);
        return text.ToString().Trim();
    }
    public static List<nint> Windows()
    {
        var windows = new List<nint>();
        EnumWindows((hwnd, _) => { if (IsWindowVisible(hwnd)) windows.Add(hwnd); return true; }, 0);
        return windows;
    }
}

internal sealed class FloatingToggle : Window
{
    readonly Button button;
    Native.Rect previousBounds;
    bool previousEnabled;
    public FloatingToggle(Action toggle)
    {
        Width = 144; Height = 32;
        WindowStyle = WindowStyle.None; ResizeMode = ResizeMode.NoResize;
        ShowInTaskbar = false; ShowActivated = false; Topmost = true;
        button = new Button { Content = "Синхронизация: выкл.", FontSize = 11 };
        Content = button;
        button.Click += (_, _) => toggle();
        SourceInitialized += (_, _) =>
        {
            var h = new WindowInteropHelper(this).Handle;
            Native.SetWindowLong(h, -20, Native.GetWindowLong(h, -20) | 0x08000000 | 0x80);
            ((HwndSource)PresentationSource.FromVisual(this)).AddHook((nint hwnd, int msg, nint w, nint l, ref bool handled) =>
            {
                if (msg == 0x0021) { handled = true; return (nint)3; } // MA_NOACTIVATE
                return 0;
            });
        };
    }
    public void Update(bool enabled, Native.Rect bounds)
    {
        if (IsVisible && previousBounds.Equals(bounds) && previousEnabled == enabled) return;
        previousBounds = bounds; previousEnabled = enabled;
        button.Content = enabled ? "Синхронизация: вкл." : "Синхронизация: выкл.";
        if (!IsVisible) Show();
        var h = new WindowInteropHelper(this).Handle;
        double scale = Math.Max(96u, Native.GetDpiForWindow(h)) / 96.0;
        int w = (int)(Width * scale), ht = (int)(Height * scale);
        // Adjacent to Notepad title bar, below the caption buttons.
        Native.SetWindowPos(h, (nint)(-1), bounds.Right - w - (int)(16 * scale),
            bounds.Top + (int)(48 * scale), w, ht, 0x0010 | 0x0040);
    }
}
