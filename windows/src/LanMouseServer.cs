using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Globalization;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

namespace LanMouse.Server
{
    internal static class Program
    {
        private const int DefaultPort = 8765;
        private const string DefaultToken = "lanmouse";

        private static volatile bool _running = true;
        private static DateTime _lastAuthWarningUtc = DateTime.MinValue;
        private static DateTime _lastValidPacketUtc = DateTime.UtcNow;

        private static int Main(string[] args)
        {
            string bindAddress = "0.0.0.0";
            int port = DefaultPort;
            string token = DefaultToken;
            bool requireToken = true;

            try
            {
                for (int i = 0; i < args.Length; i++)
                {
                    string arg = args[i];
                    switch (arg)
                    {
                        case "-b":
                        case "--bind":
                            bindAddress = RequireValue(args, ref i, arg);
                            break;
                        case "-p":
                        case "--port":
                            int parsedPort;
                            if (!int.TryParse(RequireValue(args, ref i, arg), out parsedPort) || parsedPort < 1 || parsedPort > 65535)
                            {
                                throw new ArgumentException("端口必须是 1-65535 之间的整数。");
                            }
                            port = parsedPort;
                            break;
                        case "-t":
                        case "--token":
                            token = RequireValue(args, ref i, arg);
                            break;
                        case "--no-auth":
                            requireToken = false;
                            break;
                        case "-h":
                        case "--help":
                            PrintHelp();
                            return 0;
                        default:
                            throw new ArgumentException("未知参数: " + arg);
                    }
                }

                if (requireToken && string.IsNullOrWhiteSpace(token))
                {
                    throw new ArgumentException("令牌不能为空。");
                }

                Console.OutputEncoding = Encoding.UTF8;
                Console.WriteLine("LanMouse Windows 服务端");
                Console.WriteLine("========================================");
                Console.WriteLine("监听地址 : " + bindAddress + ":" + port);
                Console.WriteLine("认证令牌 : " + (requireToken ? token : "<已关闭>"));
                Console.WriteLine("局域网 IP:");
                foreach (string ip in GetLocalIPv4Addresses())
                {
                    Console.WriteLine("  " + ip);
                }
                Console.WriteLine();
                Console.WriteLine("请在 Android 端填写上面的 IP、端口和令牌。");
                Console.WriteLine("按 Ctrl+C 停止。移动鼠标时不会刷日志。");
                Console.WriteLine();

                Console.CancelKeyPress += delegate(object sender, ConsoleCancelEventArgs eventArgs)
                {
                    eventArgs.Cancel = true;
                    _running = false;
                };

                IPAddress parsedBindAddress = IPAddress.Parse(bindAddress);
                using (UdpClient udp = new UdpClient(new IPEndPoint(parsedBindAddress, port)))
                {
                    udp.Client.ReceiveTimeout = 500;
                    while (_running)
                    {
                        IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
                        byte[] data;
                        try
                        {
                            data = udp.Receive(ref remote);
                        }
                        catch (SocketException ex)
                        {
                            if (ex.SocketErrorCode == SocketError.TimedOut)
                            {
                                if ((DateTime.UtcNow - _lastValidPacketUtc).TotalSeconds >= 5.0)
                                {
                                    MouseController.ReleaseAll();
                                }
                                continue;
                            }
                            throw;
                        }

                        HandlePacket(udp, data, remote, token, requireToken);
                    }
                }

                Console.WriteLine("服务端已停止。");
                return 0;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("启动失败: " + ex.Message);
                return 1;
            }
        }

        private static string RequireValue(string[] args, ref int index, string option)
        {
            if (index + 1 >= args.Length)
            {
                throw new ArgumentException(option + " 缺少参数值。");
            }
            index++;
            return args[index];
        }

        private static void PrintHelp()
        {
            Console.WriteLine("用法: LanMouseServer.exe [选项]");
            Console.WriteLine("  -b, --bind <IP>      监听地址，默认 0.0.0.0");
            Console.WriteLine("  -p, --port <端口>    UDP 端口，默认 8765");
            Console.WriteLine("  -t, --token <令牌>   认证令牌，默认 lanmouse");
            Console.WriteLine("      --no-auth        关闭认证（不建议在公共局域网使用）");
            Console.WriteLine("  -h, --help           显示帮助");
        }

        private static void HandlePacket(UdpClient udp, byte[] data, IPEndPoint remote, string expectedToken, bool requireToken)
        {
            string text;
            try
            {
                text = Encoding.UTF8.GetString(data);
            }
            catch
            {
                return;
            }

            Dictionary<string, object> packet;
            try
            {
                JavaScriptSerializer serializer = new JavaScriptSerializer();
                packet = serializer.Deserialize<Dictionary<string, object>>(text);
            }
            catch
            {
                return;
            }

            if (packet == null)
            {
                return;
            }

            string type = GetString(packet, "type");
            if (type == "discover")
            {
                IPEndPoint localEndpoint = udp.Client.LocalEndPoint as IPEndPoint;
                int discoveryPort = localEndpoint == null ? 0 : localEndpoint.Port;

                Dictionary<string, object> response = new Dictionary<string, object>();
                response["v"] = 1;
                response["type"] = "discovery";
                response["name"] = Environment.MachineName;
                response["port"] = discoveryPort;

                JavaScriptSerializer responseSerializer = new JavaScriptSerializer();
                byte[] responseBytes = Encoding.UTF8.GetBytes(responseSerializer.Serialize(response));
                udp.Send(responseBytes, responseBytes.Length, remote);
                Console.WriteLine("[发现] 已响应 " + remote + " 的局域网搜索。");
                return;
            }

            if (requireToken)
            {
                string receivedToken = GetString(packet, "token");
                if (!SecureEquals(receivedToken, expectedToken))
                {
                    if ((DateTime.UtcNow - _lastAuthWarningUtc).TotalSeconds >= 2)
                    {
                        _lastAuthWarningUtc = DateTime.UtcNow;
                        Console.WriteLine("[拒绝] 令牌错误，来源 " + remote);
                    }
                    return;
                }
            }

            _lastValidPacketUtc = DateTime.UtcNow;

            try
            {
                if (type == "ping")
                {
                    byte[] response = Encoding.UTF8.GetBytes("{\"type\":\"pong\",\"v\":1}");
                    udp.Send(response, response.Length, remote);
                    Console.WriteLine("[连接] 已收到 " + remote + " 的测试请求。");
                }
                else if (type == "move")
                {
                    double dx = ClampDouble(ToDouble(packet, "dx"), -1000.0, 1000.0);
                    double dy = ClampDouble(ToDouble(packet, "dy"), -1000.0, 1000.0);
                    if (Math.Abs(dx) > 0.0001 || Math.Abs(dy) > 0.0001)
                    {
                        MouseController.MoveRelative(dx, dy);
                    }
                }
                else if (type == "button")
                {
                    MouseController.Button(GetString(packet, "button"), GetString(packet, "action"));
                }
                else if (type == "scroll")
                {
                    int delta = Clamp(ToInt(packet, "delta"), -1200, 1200);
                    if (delta != 0)
                    {
                        MouseController.Scroll(delta);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine("[错误] " + remote + ": " + ex.Message);
            }
        }

        private static bool SecureEquals(string left, string right)
        {
            if (left == null || right == null)
            {
                return false;
            }

            int difference = left.Length ^ right.Length;
            int length = Math.Min(left.Length, right.Length);
            for (int i = 0; i < length; i++)
            {
                difference |= left[i] ^ right[i];
            }
            return difference == 0;
        }

        private static string GetString(Dictionary<string, object> packet, string key)
        {
            object value;
            if (!packet.TryGetValue(key, out value) || value == null)
            {
                return string.Empty;
            }
            return Convert.ToString(value, CultureInfo.InvariantCulture);
        }

        private static int ToInt(Dictionary<string, object> packet, string key)
        {
            object value;
            if (!packet.TryGetValue(key, out value) || value == null)
            {
                return 0;
            }

            try
            {
                return Convert.ToInt32(value, CultureInfo.InvariantCulture);
            }
            catch
            {
                return 0;
            }
        }

        private static double ToDouble(Dictionary<string, object> packet, string key)
        {
            object value;
            if (!packet.TryGetValue(key, out value) || value == null)
            {
                return 0.0;
            }

            try
            {
                return Convert.ToDouble(value, CultureInfo.InvariantCulture);
            }
            catch
            {
                return 0.0;
            }
        }
        private static double ClampDouble(double value, double min, double max)
        {
            if (value < min) return min;
            if (value > max) return max;
            return value;
        }

        private static int Clamp(int value, int min, int max)
        {
            if (value < min) return min;
            if (value > max) return max;
            return value;
        }

        private static IEnumerable<string> GetLocalIPv4Addresses()
        {
            List<string> result = new List<string>();
            try
            {
                foreach (NetworkInterface network in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (network.OperationalStatus != OperationalStatus.Up)
                    {
                        continue;
                    }
                    if (network.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                    {
                        continue;
                    }

                    foreach (UnicastIPAddressInformation address in network.GetIPProperties().UnicastAddresses)
                    {
                        if (address.Address.AddressFamily != AddressFamily.InterNetwork)
                        {
                            continue;
                        }
                        string ip = address.Address.ToString();
                        if (!ip.StartsWith("169.254.", StringComparison.Ordinal))
                        {
                            result.Add(ip);
                        }
                    }
                }
            }
            catch
            {
                // IP 枚举失败不影响指定 IP 连接，只是不显示候选地址。
            }

            if (result.Count == 0)
            {
                result.Add("127.0.0.1");
            }
            return result;
        }
    }

    internal static class MouseController
    {
        private const uint INPUT_MOUSE = 0;
        private const uint MOUSEEVENTF_MOVE = 0x0001;
        private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
        private const uint MOUSEEVENTF_LEFTUP = 0x0004;
        private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
        private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
        private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
        private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
        private const uint MOUSEEVENTF_WHEEL = 0x0800;
        private const int WHEEL_DELTA = 120;

        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT
        {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct INPUT_UNION
        {
            [FieldOffset(0)]
            public MOUSEINPUT mi;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT
        {
            public uint type;
            public INPUT_UNION u;
        }

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        private static double _remainderX;
        private static double _remainderY;

        public static void MoveRelative(double dx, double dy)
        {
            double totalX = dx + _remainderX;
            double totalY = dy + _remainderY;
            int moveX = (int)Math.Truncate(totalX);
            int moveY = (int)Math.Truncate(totalY);
            _remainderX = totalX - moveX;
            _remainderY = totalY - moveY;

            if (moveX != 0 || moveY != 0)
            {
                Send(MOUSEEVENTF_MOVE, moveX, moveY, 0);
            }
        }

        public static void Button(string buttonName, string action)
        {
            string button = (buttonName ?? string.Empty).ToLowerInvariant();
            string normalizedAction = (action ?? string.Empty).ToLowerInvariant();

            uint downFlag;
            uint upFlag;
            if (button == "left")
            {
                downFlag = MOUSEEVENTF_LEFTDOWN;
                upFlag = MOUSEEVENTF_LEFTUP;
            }
            else if (button == "right")
            {
                downFlag = MOUSEEVENTF_RIGHTDOWN;
                upFlag = MOUSEEVENTF_RIGHTUP;
            }
            else if (button == "middle")
            {
                downFlag = MOUSEEVENTF_MIDDLEDOWN;
                upFlag = MOUSEEVENTF_MIDDLEUP;
            }
            else
            {
                throw new ArgumentException("未知鼠标按键: " + buttonName);
            }

            if (normalizedAction == "down")
            {
                SetButtonState(button, true);
                Send(downFlag, 0, 0, 0);
            }
            else if (normalizedAction == "up")
            {
                SetButtonState(button, false);
                Send(upFlag, 0, 0, 0);
            }
            else if (normalizedAction == "click")
            {
                SetButtonState(button, true);
                Send(downFlag, 0, 0, 0);
                Send(upFlag, 0, 0, 0);
                SetButtonState(button, false);
            }
            else
            {
                throw new ArgumentException("未知按键动作: " + action);
            }
        }

        private static bool _leftDown;
        private static bool _rightDown;
        private static bool _middleDown;

        private static void SetButtonState(string button, bool isDown)
        {
            if (button == "left")
            {
                _leftDown = isDown;
            }
            else if (button == "right")
            {
                _rightDown = isDown;
            }
            else if (button == "middle")
            {
                _middleDown = isDown;
            }
        }

        public static void ReleaseAll()
        {
            if (_leftDown)
            {
                _leftDown = false;
                Send(MOUSEEVENTF_LEFTUP, 0, 0, 0);
            }
            if (_rightDown)
            {
                _rightDown = false;
                Send(MOUSEEVENTF_RIGHTUP, 0, 0, 0);
            }
            if (_middleDown)
            {
                _middleDown = false;
                Send(MOUSEEVENTF_MIDDLEUP, 0, 0, 0);
            }
        }

        public static void Scroll(int delta)
        {
            if (delta == 0)
            {
                return;
            }

            int clicks = delta / WHEEL_DELTA;
            if (clicks == 0)
            {
                clicks = delta > 0 ? 1 : -1;
            }
            clicks = Math.Max(-10, Math.Min(10, clicks));
            Send(MOUSEEVENTF_WHEEL, 0, 0, clicks * WHEEL_DELTA);
        }

        private static void Send(uint flags, int dx, int dy, int mouseData)
        {
            INPUT input = new INPUT();
            input.type = INPUT_MOUSE;
            input.u.mi.dx = dx;
            input.u.mi.dy = dy;
            input.u.mi.mouseData = unchecked((uint)mouseData);
            input.u.mi.dwFlags = flags;
            input.u.mi.time = 0;
            input.u.mi.dwExtraInfo = IntPtr.Zero;

            INPUT[] inputs = new INPUT[] { input };
            uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
            if (sent != 1)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }
        }
    }
}