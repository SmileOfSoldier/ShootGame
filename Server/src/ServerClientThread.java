import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

/**
 * 玩家服务线程
 */
class ServerClientThread extends Thread{
    private Client client=null;
    private Socket socket;
    private PrintStream sendStream;
    private BufferedReader getStream;
    private boolean isConnected=false;//是否连接
    private boolean isLogin=false;//是否登陆

    /**
     * 获取此线程实例对象的Gamer
     * @return
     */
    public Client getClient(){
        return client;
    }
    /**
     * 玩家服务线程构造
     * @param socket 属于实例对象(一位玩家)的Socket通道
     */
    public ServerClientThread(Socket socket, PrintStream sendStream, BufferedReader getStream){
        this.socket=socket;
        this.sendStream=sendStream;//获取写出流
        this.getStream=getStream;//获取写入流
        System.out.println("成功创建一个玩家服务线程");
    }

    /**
     * 玩家服务线程run函数
     */
    public void run(){
        String line=null;//接收到的初始字符串（信息）
        String command = null;//当前获取的信息需要执行的命令
        String realMessage = null;//去除头部命令的信息
        //线程不被interrupted则持续接收玩家发来的信息
        while(!this.isInterrupted()) {
            if (isConnected) {
                try {
                    //TODO:服务线程run待完成
                    System.out.println("线程阻塞中等待命令。");
                    line = getStream.readLine();//线程堵塞  读取发来的消息
                    System.out.println("收到一个命令信息"+line);
                    /**
                     * 如果是登陆则采取如下操作
                     */
                    if (!isLogin && line.startsWith(Sign.Login)) {
                        try {
                            System.out.println("进度登陆函数");
                            int loginResult = check.checkLoginInfo(line);
                            System.out.println("登陆结果为"+loginResult);//1为成功 -1为账号未注册  为密码错误
                            switch (loginResult){
                                case 1:{
                                    isLogin = true;//密码成功则将当前玩家的服务线程登陆置为true
                                    client = check.creatPlayer(line);//创建依据line的player对象
                                    creatServer.onlineClients.add(client);//在在线玩家列表中加入玩家
                                    creatServer.clientPrintStreamMap.put(client,sendStream);//加入玩家写流
                                    Client[] allOnlineClients=null;
                                    creatServer.onlineClients.toArray(allOnlineClients);//打包所有在线玩家
                                    ServerGameRoom[] allServerGameRoom=null;
                                    creatServer.allGameRoom.toArray(allServerGameRoom);//打包所有房间消息
                                    //打包发送初始化消息
                                    Gson gson=new Gson();
                                    String allclientsStr=gson.toJson(allOnlineClients);
                                    String roomStr=gson.toJson(allServerGameRoom);
                                    String clientStr=gson.toJson(client);
                                    //打包发送
                                    sendStream.println(Sign.LoginSuccess);
                                    sendStream.println(allclientsStr+Sign.SplitSign+roomStr+Sign.SplitSign+clientStr);
                                    break;
                                }
                                case -1:{
                                    sendCommand(Sign.IsNotRegistered);//返回账号还未注册的消息
                                    break;
                                }
                                case 0:{
                                    sendCommand(Sign.WrongPassword);//返回密码错误的消息
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    /**
                     * 如果接收到注册请求
                     */
                    else if (!isLogin && line.startsWith(Sign.Register)) {
                        System.out.println("收到注册请求开始注册流程。");
                        //分割命令与内容
                        realMessage = check.getRealMessage(line, Sign.Register);
                        String playerid = realMessage.split(Sign.SplitSign)[0];
                        String playerPassword = realMessage.split(Sign.SplitSign)[1];
                        if(!check.isRegistered(playerid)){
                             saveorreadInfo.savePlayerInfo(new Client(playerid, playerPassword));//注册一个Player并保存到文件
                             sendCommand(Sign.RegisterSuccess);//返回注册成功信息
                        }
                        else sendCommand(Sign.IsRegistered);//否则返回已经注册过的消息
                    }
                    /**
                     * 如果收到创建房间信息
                     */
                    else if(isLogin&&line.startsWith(Sign.CreateRoom)){
                        System.out.println("收到创建房间请求。");
                        realMessage=check.getRealMessage(line,Sign.CreateRoom);
                        String roomname=realMessage.split(Sign.SplitSign)[0];
                        ServerGameRoom serverGameRoom=new ServerGameRoom(client.getId(),client,roomname);//以玩家的id和对象还有发来的房间名字创建房间
                        System.out.println("成功创建名字为"+roomname+"的房间。");
                        Gson gson=new Gson();
                        String s=gson.toJson(serverGameRoom);
                        for(PrintStream sendstream:creatServer.clientPrintStreamMap.values()){
                            sendstream.println(Sign.NewRoomCreate+s);
                        }//发送给所有玩家房间创建信息
                        System.out.println("向所有玩家发送玩家房间信息创建标识。");
                    }
                    /**
                     * 如果收到加入房间信息
                     */
                    else if(isLogin&&line.startsWith(Sign.EnterRoom)){
                        realMessage=check.getRealMessage(line,Sign.EnterRoom);
                        String clientid=realMessage.split(Sign.SplitSign)[0];//获取创建者的名字与房间名字
                        String roomid=realMessage.split(Sign.SplitSign)[1]; //获取需要加入的房间名
                        ServerGameRoom serverGameRoom=null;
                        System.out.println("收到来自"+clientid+"加入"+roomid+"房间的请求");
                        //找到房间
                        int flag=-1;
                        for(int i=0;i<creatServer.allGameRoom.size();i++){
                            if(creatServer.allGameRoom.get(i).getId().equals(roomid)) flag=i;
                            serverGameRoom=creatServer.allGameRoom.get(i);
                            break;
                        }
                        System.out.println("找到需要加入的房间名字为"+serverGameRoom.getId());
                        //转发给这房间内所有其他玩家
                        List<Client> list=serverGameRoom.getAllClients();
                        for(Client c:list){
                            PrintStream printStream=creatServer.clientPrintStreamMap.get(c);
                            printStream.println(Sign.NewClientEnter+clientid+Sign.SplitSign+roomid);//转发给房间其他在线玩家xxx进入
                        }
                        System.out.println("开始转发给该房间其他玩家"+client.getId()+"加入了房间");
                        //将当前玩家加入到指定的房间内
                        serverGameRoom.addClient(client);
                        //将当前玩家所属房间指定为此房间
                        client.setGameRoom(serverGameRoom);
                    }
                    /**
                     * 如果收到T人的消息（房主可用）
                     */
                    else if(isLogin&&line.startsWith(Sign.TickFromRoom)){
                        realMessage=check.getRealMessage(line,Sign.TickFromRoom);
                        String targetId=realMessage.split(Sign.SplitSign)[0];//获取被T玩家id
                        String roomid=realMessage.split(Sign.SplitSign)[1];//获取房间ID
                        ServerGameRoom serverGameRoom=null;
                        System.out.println("收到来自"+client.getId()+"的T人请求。");
                        for(ServerGameRoom room:creatServer.allGameRoom){
                            if(roomid.equals(room.getId())) serverGameRoom=room;
                            break;
                        }
                        if(serverGameRoom.getId().equals(client.getId())) {//如果为房主
                            List<Client> list = serverGameRoom.getAllClients();
                            for (Client c : list) {
                                PrintStream printStream = creatServer.clientPrintStreamMap.get(c);
                                printStream.println(Sign.OneClientTicked + targetId + Sign.SplitSign + roomid);
                            }//发送给房间内所有玩家xxx被T除
                        }
                        //该房间移除该玩家(同时将该玩家的所属房间重新置空)
                        serverGameRoom.removeClient(targetId);

                    }
                    /**
                     * 如果收到离开房间的消息
                     */
                    else if(isLogin&&line.startsWith(Sign.ClientLeaveRoom)){
                        System.out.println("服务器收到"+client.getId()+"发来的离开房间的信息。");
                        ServerGameRoom serverGameRoom=null;
                        serverGameRoom=client.getRoom();//获取玩家当前所在房间
                        serverGameRoom.removeClient(client.getId());//当前所在房间移除当前玩家
                        List<Client> allClientsIn=serverGameRoom.getAllClients();
                        //向房间所有玩家发该玩家退出信息
                        for(Client c:allClientsIn){
                            PrintStream printStream=creatServer.clientPrintStreamMap.get(c);
                            printStream.println(Sign.ClientLeaveRoom+client.getId());//发送玩家退出房间指令加退出玩家id
                        }

                    }
                    /**
                     * 如果收到开始游戏的信息（房主可用）
                     */
                    else if(isLogin&&line.startsWith(Sign.StartGame)){

                    }
                    /**
                     * 如果收到注销请求(玩家返回到登陆界面)
                     */
                    else if (isLogin&&line.startsWith(Sign.Logout)){

                    }
                    /**
                     * 收到聊天信息命令
                     */
                    else if(isLogin&&line.startsWith(Sign.SendPublicMessage)){
                        realMessage=check.getRealMessage(line,Sign.SendPublicMessage);
                        ServerGameRoom serverGameRoom=null;
                        serverGameRoom=client.getRoom();
                        for(Client c:serverGameRoom.getAllClients()){
                            PrintStream printStream=creatServer.clientPrintStreamMap.get(c);
                            printStream.println(Sign.FromServerMessage+"(来自"+client.getId()+"的) "+realMessage);
                        }
                    }
                    /**
                     * 如果收到断开连接请求（返回到单人与多人游戏选择界面)
                     */
                    else if(isLogin&&line.startsWith(Sign.Disconnect)){
                        stopThisClient(Sign.SuccessDisconnected,sendStream,getStream);//关闭此服务线程 tips:原因：玩家请求断开连接
                    }

                    //TODO:待完成的玩家服务线程

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     *
     * @param flag
     */
    public void setisConnected(boolean flag){
        isConnected=flag;
    }

    /**
     * 发送命令函数
     * @param command 发送的命令
     */
    public void sendCommand(String command){
        sendStream.println(command);
        sendStream.flush();
    }

    /**
     * 用于注销当前服务线程服务的玩家账号
     * //TODO:
     */
    public void LogoutPlayer(){

    }

    /**
     *
     * @return 返回发送流
     */
    public PrintStream getSendStream(){
        return sendStream;
    }

    /**
     *
     * @return 收取流
     */
    public BufferedReader getGetStream(){
        return getStream;
    }
    /**
     * 停止当前服务线程实例对象的运行并进行扫尾工作
     * @param reson 停止（拒绝连接或被T除的原因)
     * @param sendStream 获取输出流以回复客户端消息和扫尾停止
     * @param getStream 获取输入流进行扫尾停止
     */
    private void stopThisClient(String reson,PrintStream sendStream,BufferedReader getStream) throws IOException {
                //发送拒绝登陆消息和停止线程
                sendStream.println(reson);
                //扫尾工作
                sendStream.flush();
                sendStream.close();
                getStream.close();
                this.interrupt();//停止玩家服务线程
    }
}