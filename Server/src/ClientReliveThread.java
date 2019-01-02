import java.io.BufferedReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.List;

/**
 * 玩家复活延时线程
 */
public class ClientReliveThread extends Thread
{
    private ServerGameRoom serverGameRoom;
    private int clientnum;
    public ClientReliveThread(ServerGameRoom serverGameRoom,int clientnum)
    {
        this.serverGameRoom=serverGameRoom;
        this.clientnum=clientnum;
    }
    public void run()
    {
        try {
            sleep(3000);
             //生成初始化出生地址的数组
            int[] randomEntrance=Info.randomArray(0,4,1);
            int i=randomEntrance[0];
            /**
             * 给所有房间内的玩家发送玩家编号为clientnum重新复活在i位置
             */
            for(Client c:serverGameRoom.getAllClients())
            {
                PrintStream sendstream=CreatServer.clientPrintStreamMap.get(c);
                sendstream.println(Sign.OnePlayerRelive+clientnum+Sign.SplitSign+i);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
