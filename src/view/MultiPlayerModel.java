package view;

import Arsenal.*;
import Weapon.*;
import bullet.*;
import com.google.gson.Gson;
import javafx.scene.transform.Rotate;
import person.*;
import reward.MedicalPackage;
import reward.RewardProp;
import reward.RewardType;
import utils.MusicPlayer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 该模式为多人在线模式
 */
public class MultiPlayerModel extends JFrame
{
    private JFrame superiorMenu;
    private static Gson gson=new Gson();
    private Point[] entrance=new Point[]{new Point(800,20),new Point(1040,280),new Point(1160,600), new Point(320,760),new Point(20,520)};         //刷怪位置
    private Point mousePoint =new Point();      //鼠标当前制作位置
    private Random random=new Random();
    public final static int CELL=20;   //每个方格的大小
    public final static int gameAreaWidth =1200;
    public final static int gameAreaHeight=950;
    public final static int gameFrameWidth=1206;
    public final static int gameFrameHeight=974;
    private JTextField healthLevelTip=new JTextField("生命值");
    public static JProgressBar healthLevel=new JProgressBar(0, Player.maxHealthPoint);        //显示玩家生命的进度条
    public static JTextField bulletLeft=new JTextField();                           //显示武器子弹剩余量
    public static JTextField killAndDieField =new  JTextField();                             //显示玩家击杀死亡数
    public static JLabel usingWeaponFlag=new JLabel();                   //玩家当前使用武器的标记图标
    public static Point []flagPoint=new Point[4];                       //玩家当前使用武器的标记图标的位置
    //存放自动步枪子弹的链表
    private static java.util.List<Bullet> automaticBulletList = Collections.synchronizedList(new LinkedList<Bullet>());
    //存放狙击枪子弹的链表
    private static java.util.List<Bullet> sniperBulletList =Collections.synchronizedList(new LinkedList<Bullet>());
    //存放地雷的链表
    private static java.util.List<Mine> mineList=Collections.synchronizedList(new LinkedList<Mine>());
    //存放手雷的链表
    private static java.util.List<Grenade> grenadeList=Collections.synchronizedList(new LinkedList<Grenade>());
    //存放掉落物品的链表
    private static java.util.List<RewardProp> rewardPropList=Collections.synchronizedList(new LinkedList<RewardProp>());
    //存放所有Person的链表
    private static java.util.List<Player> playerList =Collections.synchronizedList(new LinkedList<Player>());
    //存放物品栏
    public static JLabel[] itemBars=new JLabel[]{new JLabel(),new JLabel(),new JLabel(),new JLabel()};
    private static Player me;                      //本地玩家
    private Timer shotThread=null;              //开火线程
    private GameArea gameArea=null;
    private Timer automaticBulletThread=null;   //自动步枪子弹飞行线程
    private Timer sniperBulletThread=null;      //狙击步枪子弹飞行线程
    private Timer playerMoveThread=null;        //玩家移动的线程
    private Timer grenadeMoveThread=null;       //控制手雷移动的线程
    MultiPlayerModel(Player me, java.util.List<Player> allPerson)
    {
        playerList =allPerson;
        MultiPlayerModel.me =me;
        gameArea=new GameArea();
        //将所有玩家加入到游戏画面中
        initialPersonMoveThread();
        //关闭窗口推出程序
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        this.setSize(gameFrameWidth,gameFrameHeight);
        this.setResizable(false);
        this.setLocationRelativeTo(null);
        this.add(gameArea);
        MusicPlayer.playActionMusic();
        this.setVisible(true);
        new GameThread(ClientPort.threadLock,ClientPort.threadCondition).start();
    }

    /**
     * 游戏数据传输线程，用来传输多人游戏时玩家之间的数据
     */
    class GameThread extends Thread
    {
        private Lock lock=null;
        private Condition condition=null;
        public GameThread(Lock lock, Condition condition)
        {
            this.lock=lock;
            this.condition=condition;
        }
        public void run()
        {
            lock.lock();
            String line=null;
            String realMessage=null;
            try
            {
                while ((line = ClientPort.getStream.readLine()) != null)
                {
                    System.out.println("游戏线程收到一个"+line+"命令");
                    //如果收到游戏结束的命令
                    if(line.startsWith(Sign.GameOver))
                    {

                    }
                    //如果收到手雷爆炸的消息
                    else if(line.startsWith(Sign.GrenadeBoom))
                    {
                        realMessage=getRealMessage(line,Sign.GrenadeBoom);
                        Grenade grenade=gson.fromJson(realMessage,Grenade.class);
                        if(grenadeList.contains(grenade))
                        {
                            grenadeList.remove(grenade);
                            //手雷爆炸特效
                            grenade.boom(gameArea);
                            //位于爆炸半径内的玩家将全部死亡
                            for (Player player : playerList)
                            {
                                //如果玩家不是死亡状态
                                if(!player.ifDie())
                                {
                                    //玩家的中心位置
                                    Point playerPoint = getCentralPoint(player.getLocation());
                                    //手雷的爆炸中心位置
                                    Point grenadePoint = getCentralPoint(grenade.getLocation());
                                    //如果该玩家位于爆炸半径内
                                    if (playerPoint.distance(grenadePoint) < grenade.getDamageRadius()) {
                                        //向服务器发送该玩家死亡的消息
                                        ClientPort.sendStream.println(Sign.OnePlayerDie + player.getId());
                                        if (player.equals(me)) {
                                            healthLevel.setValue(0);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    //如果收到地雷爆炸的消息
                    else if(line.startsWith(Sign.MineBoom))
                    {
                        String mineStr=getRealMessage(line,Sign.MineBoom);
                        Mine mine=gson.fromJson(mineStr,Mine.class);
                        if(mineList.contains(mine))
                        {
                            mineList.remove(mine);
                            //地雷爆炸特效
                            mine.boom(gameArea, mine.getLocation());
                            //地雷的爆炸半径
                            int damageRadius = mine.getDamageRadius();
                            //位于地雷爆炸半径内的玩家将全部死亡
                            for (Player player : playerList)
                            {
                                //如果玩家不是死亡状态
                                if(!player.ifDie())
                                {
                                    //玩家的中心坐标
                                    Point playerPoint = getCentralPoint(player.getLocation());
                                    //地雷的爆炸中心坐标
                                    Point minePoint = getCentralPoint(mine.getLocation());
                                    //如果玩家中心坐标于手雷的中心坐标小于爆炸半径
                                    if (playerPoint.distance(minePoint) < damageRadius) {
                                        mine.getFromPerson().addKillNum(1);
                                        //向服务器发送该玩家死亡的消息
                                        ClientPort.sendStream.println(Sign.OnePlayerDie + player.getId());
                                        if (player.equals(me)) {
                                            healthLevel.setValue(0);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    //如果收到玩家死亡的消息
                    else if(line.startsWith(Sign.OnePlayerDie))
                    {
                        int index=Integer.parseInt(getRealMessage(line,Sign.OnePlayerDie));
                        Player player=playerList.get(index);
                        if(!player.ifDie())
                        {
                            player.setDie(true);
                            player.dieSpecialEffect(gameArea);
                            player.setVisible(false);
                        }
                    }
                    //如果收到玩家复活的消息
                    else if(line.startsWith(Sign.OnePlayerRelive))
                    {
                        realMessage=getRealMessage(line,Sign.OnePlayerRelive);
                        int playerIndex=Integer.parseInt(realMessage.split(Sign.SplitSign)[0]);
                        int relivePointIndex=Integer.parseInt(realMessage.split(Sign.SplitSign)[1]);
                        Player player=playerList.get(playerIndex);
                        if(player.ifDie())
                        {
                            player.addHealthPoint(Player.maxHealthPoint);
                            player.setLocation(entrance[relivePointIndex]);
                            player.setDie(false);
                            player.setVisible(true);
                            if (player.equals(me))
                            {
                                healthLevel.setValue(player.getHealthPoint());
                            }
                        }
                    }
                    //如果收到玩家移动的消息
                    else if(line.startsWith(Sign.PlayerMove))
                    {
                        realMessage=getRealMessage(line,Sign.PlayerMove);
                        int index=Integer.parseInt(realMessage.split(Sign.SplitSign)[0]);
                        int dir=Integer.parseInt(realMessage.split(Sign.SplitSign)[1]);
                        Player player=playerList.get(index);
                        switch (dir)
                        {
                            case MoveDirection.left:
                                player.setlSpeed(TravelSpeed.personTravelSpeed);
                                break;
                            case MoveDirection.right:
                                player.setrSpeed(TravelSpeed.personTravelSpeed);
                                break;
                            case MoveDirection.up:
                                player.setuSpeed(TravelSpeed.personTravelSpeed);
                                break;
                            case MoveDirection.down:
                                player.setdSpeed(TravelSpeed.personTravelSpeed);
                                break;
                        }
                    }
                    //如果收到玩家停止移动的消息
                    else if(line.startsWith(Sign.PlayerStopMove))
                    {
                        realMessage=getRealMessage(line,Sign.PlayerMove);
                        int index=Integer.parseInt(realMessage.split(Sign.SplitSign)[0]);
                        int dir=Integer.parseInt(realMessage.split(Sign.SplitSign)[1]);
                        Player player=playerList.get(index);
                        switch (dir)
                        {
                            case MoveDirection.left:
                                player.setlSpeed(0);
                                break;
                            case MoveDirection.right:
                                player.setrSpeed(0);
                                break;
                            case MoveDirection.up:
                                player.setuSpeed(0);
                                break;
                            case MoveDirection.down:
                                player.setdSpeed(0);
                                break;
                        }
                    }
                }
            }
            catch (IOException ioe)
            {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * 初始化人物移动线程
     */
    private void initialPersonMoveThread()
    {
        /**
         * 初始化控制玩家移动的线程
         */
        playerMoveThread=new Timer(me.getSpeed(), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                for(Player player: playerList)
                {
                    //如果玩家不是死亡状态，则可以进行移动
                    if(!player.ifDie())
                    {
                        Point oldPoint = player.getLocation();
                        Point newPoint = new Point(oldPoint.x + player.getrSpeed() - player.getlSpeed(), oldPoint.y + player.getdSpeed() - player.getuSpeed());
                        if (!ifHitWall(newPoint, player.getRadius(), false))
                            player.setLocation(newPoint);
                        for (Mine mine : mineList) {
                            //如果有玩家踩到了地雷，向服务器发送地雷爆炸的消息
                            if (ifStepMine(mine, player)) {
                                Gson gson = new Gson();
                                String mineStr = gson.toJson(mine);
                                ClientPort.sendStream.println(Sign.MineBoom + mineStr);
                            }
                        }
                    }

                }
            }
        });
        playerMoveThread.start();
    }
    /**
     * 内部类
     * 游戏的画面显示区域
     *
     */
    class GameArea extends JPanel
    {
        GameArea()
        {
            //将玩家模型添加到游戏画面中
            for(Player player:playerList)
            {
                this.add(player);
            }

            initialBulletMoveThread();
            healthLevel.setStringPainted(true); //显示玩家生命值百分比
            healthLevel.setSize(120,30);
            healthLevel.setLocation(100,10);
            healthLevel.setForeground(new Color(0xFD2016));
            healthLevel.setValue(me.getHealthPoint());

            healthLevelTip.setLocation(10,10);
            healthLevelTip.setSize(70,30);
            healthLevelTip.setFont(new Font(null,Font.BOLD,20));
            healthLevelTip.setBackground(new Color(0xFD2016));
            healthLevelTip.setEditable(false);

            bulletLeft.setFont(new Font(null,Font.BOLD,20));
            bulletLeft.setSize(180,30);
            bulletLeft.setLocation(10,50);
            bulletLeft.setEditable(false);
            bulletLeft.setBackground(new Color(0x75BCE2));
            bulletLeft.setText("子弹："+30+"/"+10000);

            killAndDieField.setText("击杀/死亡：(0/0)");
            killAndDieField.setFont(new Font(null,Font.BOLD,20));
            killAndDieField.setSize(180,30);
            killAndDieField.setLocation(1000,10);
            killAndDieField.setBackground(new Color(0xED4078));

            URL url= SinglePersonModel.class.getResource("/images/logo/usingWeaponFlag.png");
            ImageIcon icon=new ImageIcon(url);
            icon.setImage(icon.getImage().getScaledInstance(20,20,Image.SCALE_DEFAULT));
            usingWeaponFlag.setSize(20, 20);
            usingWeaponFlag.setIcon(icon);
            flagPoint[0]=new Point(10,820);
            flagPoint[1]=new Point(400,820);
            flagPoint[2]=new Point(790,820);
            flagPoint[3]=new Point(1020,820);

            this.add(usingWeaponFlag);
            this.add(healthLevel);
            this.add(healthLevelTip);
            this.add(bulletLeft);
            this.add(killAndDieField);
            this.setSize(1200, 950);
            this.setLayout(null);           //设置为绝对布局
        }

        /**
         * 初始化物品栏
         */
        private void initialItemBars()
        {
            itemBars[0].setSize(300,150);
            itemBars[1].setSize(300,150);
            itemBars[2].setSize(150,150);
            itemBars[3].setSize(150,150);
            itemBars[0].setLocation(0,800);
            itemBars[1].setLocation(400,800);
            itemBars[2].setLocation(800,800);
            itemBars[3].setLocation(1050,800);

            for(int i=0;i<itemBars.length;i++)
            {
                int k=i+1;
                int width=itemBars[i].getWidth();
                int height=itemBars[i].getHeight();
                URL url= SinglePersonModel.class.getResource("/images/itemBars/itemBar"+k+".png");
                ImageIcon icon=new ImageIcon(url);
                icon.setImage(icon.getImage().getScaledInstance(width,height,Image.SCALE_DEFAULT));
                itemBars[i].setIcon(icon);
                this.add(itemBars[i]);
            }
        }

        /**
         * 初始化子弹移动线程
         */
        private void initialBulletMoveThread()
        {
            initialItemBars();
            /**
             * 键盘监听器监听玩家移动，玩家切换武器，装填子弹
             */
            this.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e)
                {
                    switch (e.getKeyCode())
                    {
                        case KeyEvent.VK_W:
                            ClientPort.sendStream.println(Sign.PlayerMove+me.getId()+Sign.SplitSign+MoveDirection.up);
                            break;
                        case KeyEvent.VK_S:
                            ClientPort.sendStream.println(Sign.PlayerMove+me.getId()+Sign.SplitSign+MoveDirection.down);
                            break;
                        case KeyEvent.VK_D:
                            ClientPort.sendStream.println(Sign.PlayerMove+me.getId()+Sign.SplitSign+MoveDirection.right);
                            break;
                        case KeyEvent.VK_A:
                            ClientPort.sendStream.println(Sign.PlayerMove+me.getId()+Sign.SplitSign+MoveDirection.left);
                            break;
                        case KeyEvent.VK_R:             //装填子弹
                            Weapon usingWeapon=me.getUsingWeapon();
                            //如果玩家当前使用的是枪,则可以换子弹
                            if(usingWeapon instanceof Gun)
                            {
                                me.reLoad();
                                int bulletLeftInGun=((Gun)usingWeapon).getBulletLeft();
                                int bulletLeftOnPerson=me.getBulletLeftOnPerson();
                                bulletLeft.setText("子弹："+bulletLeftInGun+"/"+bulletLeftOnPerson);
                            }
                            break;
                        //玩家捡起道具
                        case KeyEvent.VK_F:
                            Point playerPoint=me.getLocation();
                            int index=-1;
                            int i=0;
                            for(RewardProp rewardProp:rewardPropList)
                            {
                                if (playerPoint.distance(rewardProp.getLocation()) < CELL)
                                {
                                    //如果道具是医疗包
                                    MusicPlayer.playPeekRewardPropMusic();
                                    rewardProp.setVisible(false);
                                    if(rewardProp.getType()==0)
                                    {
                                        MedicalPackage medicalPackage=(MedicalPackage) rewardProp.getReward();
                                        me.addHealthPoint(medicalPackage.getHealthPoint());
                                    }
                                    //如果是地雷或者是手雷
                                    else if(rewardProp.getType()== RewardType.Mine || rewardProp.getType()==RewardType.Grenade)
                                    {
                                        me.peekWeapon((Weapon) rewardProp.getReward(),1);
                                        int left=me.getBulletLeftOnPerson();
                                        bulletLeft.setText("子弹："+left);
                                    }
                                    //如果道具是枪
                                    else
                                    {
                                        me.peekWeapon((Weapon) rewardProp.getReward(),0);
                                    }
                                    index=i;
                                }
                                i++;
                            }
                            if(index!=-1)
                            {
                                gameArea.remove(rewardPropList.get(index));
                                rewardPropList.remove(index);
                            }
                            break;
                        //切换武器
                        case KeyEvent.VK_1: {
                            me.changeWeapon(WeaponType.automaticRifle, gameArea);
                            if (shotThread != null && shotThread.isRunning())
                                shotThread.stop();
                            int bulletLeftInGun = ((Gun) me.getUsingWeapon()).getBulletLeft();
                            int bulletLeftOnPerson = me.getBulletLeftOnPerson();
                            bulletLeft.setText("子弹：" + bulletLeftInGun + "/" + bulletLeftOnPerson);
                            Point point= flagPoint[me.getUsingWeaponType()-1];
                            usingWeaponFlag.setLocation(point);
                            break;
                        }
                        case KeyEvent.VK_2:
                            {
                            me.changeWeapon(WeaponType.sniperRifle, gameArea);
                            if (shotThread != null && shotThread.isRunning())
                                shotThread.stop();
                            int bulletLeftInGun = ((Gun) me.getUsingWeapon()).getBulletLeft();
                            int bulletLeftOnPerson = me.getBulletLeftOnPerson();
                            bulletLeft.setText("子弹：" + bulletLeftInGun + "/" + bulletLeftOnPerson);
                            Point point=flagPoint[me.getUsingWeaponType()-1];
                            usingWeaponFlag.setLocation(point);
                            break;
                        }
                        case KeyEvent.VK_3: {
                            me.changeWeapon(WeaponType.grenade, gameArea);
                            if (shotThread != null && shotThread.isRunning())
                                shotThread.stop();
                            int grenadeLeft=me.getBulletLeftOnPerson();
                            bulletLeft.setText("手雷："+grenadeLeft);
                            Point point= flagPoint[me.getUsingWeaponType()-1];
                            usingWeaponFlag.setLocation(point);
                            break;
                        }
                        case KeyEvent.VK_4: {
                            me.changeWeapon(WeaponType.mine, gameArea);
                            if (shotThread != null && shotThread.isRunning())
                                shotThread.stop();
                            int mineLeft=me.getBulletLeftOnPerson();
                            bulletLeft.setText("地雷："+mineLeft);
                            Point point= flagPoint[me.getUsingWeaponType()-1];
                            usingWeaponFlag.setLocation(point);
                            break;
                        }
                    }
                }
                @Override
                //玩家松开移动按钮
                public void keyReleased(KeyEvent e)
                {
                    switch (e.getExtendedKeyCode())
                    {
                        case KeyEvent.VK_W:
                           ClientPort.sendStream.println(Sign.PlayerStopMove+me.getId()+Sign.SplitSign+MoveDirection.up);
                            break;
                        case KeyEvent.VK_S:
                            ClientPort.sendStream.println(Sign.PlayerStopMove+me.getId()+Sign.SplitSign+MoveDirection.down);
                            break;
                        case KeyEvent.VK_D:
                            ClientPort.sendStream.println(Sign.PlayerStopMove+me.getId()+Sign.SplitSign+MoveDirection.right);
                            break;
                        case KeyEvent.VK_A:
                            ClientPort.sendStream.println(Sign.PlayerStopMove+me.getId()+Sign.SplitSign+MoveDirection.left);
                            break;
                    }
                }
            });
            //将JPanel设置成可焦点化
            this.setFocusable(true);

            this.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e)
                {
                    Point oldPoint= mousePoint;
                    mousePoint =e.getPoint();
                }
                public void mouseMoved(MouseEvent e)
                {
                    mousePoint =e.getPoint();
                }
            });
            //玩家射击
            this.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent mouseEvent)        //非枪类武器攻击
                {
                    Weapon weapon= me.getUsingWeapon();
                    Point startPoint=getCentralPoint(me.getLocation());
                    if(!(weapon instanceof Gun))
                    {
                        attack(startPoint, mousePoint, me);
                    }

                }
                public void mousePressed(MouseEvent mouseEvent)         //枪类武器攻击
                {
                    Weapon weapon= me.getUsingWeapon();
                    Point startPoint = getCentralPoint(me.getLocation());
                    if(weapon instanceof Gun)
                    {
                        int fireRate = ((Gun) weapon).getFireRate();
                        attack(startPoint, mousePoint, me);
                        shotThread = new Timer(fireRate, new ActionListener() {       //玩家开火
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                Point startPoint = getCentralPoint(me.getLocation());
                                attack(startPoint, mousePoint, me);
                            }
                        });
                        shotThread.start();
                    }
                }
                @Override
                public void mouseReleased(MouseEvent e)
                {
                    Weapon weapon= me.getUsingWeapon();
                    if(weapon instanceof Gun && ((Gun)weapon).ifContinuedShot())
                    {
                        MusicPlayer.stopContinueAttackMusic();
                    }
                    me.setAttacking(false);
                    if(shotThread!=null && shotThread.isRunning())
                        shotThread.stop();
                }       //玩家停止开火
            });
            /**
             * 初始化控制狙击步枪子弹移动的线程
             */
            sniperBulletThread=new Timer(BulletSpeed.sniperBulletSpeed, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    if(!sniperBulletList.isEmpty())
                    {
                        int i=-1;
                        Bullet[] deleteBullet=new Bullet[sniperBulletList.size()];
                        for(Bullet bullet:sniperBulletList)
                        {
                            boolean flag=false;             //标记该子弹是否击中人
                            Point oldPoint=bullet.getLocation();
                            Point newPoint=new Point(oldPoint.x+bullet.getxSpeed(),oldPoint.y+bullet.getySpeed());
                            bullet.setLocation(newPoint);
                            for(Person person: playerList)          //判断每个人是否被子弹击中
                            {
                                if((ifHitPerson(bullet,person)) && !person.ifDie() && !person.equals(bullet.getFromPerson()))    //如果击中AI
                                {
                                    flag=true;
                                    bullet.setVisible(false);                   //击中目标后子弹消失
                                    int healthPoint =person.getHealthPoint();
                                    if(healthPoint-bullet.getDamageValue()<=0)      //如果目标死亡
                                    {
                                        bullet.getFromPerson().addKillNum(1);       //这颗子弹的所有者击杀数加1
                                        person.dieSpecialEffect(gameArea);
                                        person.setVisible(false);
                                        person.setDie(true);
                                        /*



                                         */
                                        createRewardProp(person.getLocation());     //掉落物品
                                    }
                                    else
                                    {
                                        person.reduceHealthPoint(bullet.getDamageValue());
                                    }
                                    deleteBullet[++i]=bullet;       //将击中目标的子弹保存起来
                                    break;
                                }
                            }
                            if(!flag && (ifHitWall(bullet.getLocation(),bullet.getRadius(),true)))    //判断子弹是否撞墙
                            {
                                deleteBullet[++i]=bullet ;           //将撞墙的子弹保存起来
                                bullet.setVisible(false);
                                // MusicPlayer.playBulletHitWallMusic();
                            }
                        }
                        if(i!=-1)
                        {
                            for(int j=0;j<=i;j++)
                            {
                                gameArea.remove(deleteBullet[j]);  //将子弹从gameArea中删除
                                sniperBulletList.remove(deleteBullet[j]);                //将子弹从自动步枪子弹中删除
                            }
                            gameArea.repaint();
                        }
                    }
                }
            });
            sniperBulletThread.start();
            /**
             *
             * 控制自动步枪自动移动的线程
             * 判断每个子弹是否撞到人，
             */
            automaticBulletThread=new Timer(BulletSpeed.automaticBulletSpeed, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    if(!automaticBulletList.isEmpty())
                    {
                        int i=-1;
                        Bullet[] deleteBullet=new Bullet[automaticBulletList.size()];
                        for(Bullet bullet:automaticBulletList)
                        {
                            boolean flag=false;             //标记该子弹是否击中人
                            Point oldPoint=bullet.getLocation();
                            Point newPoint=new Point(oldPoint.x+bullet.getxSpeed(),oldPoint.y+bullet.getySpeed());
                            bullet.setLocation(newPoint);
                            for(Person person: playerList)          //判断每个敌人是否被子弹击中
                            {
                                if((ifHitPerson(bullet,person)) && !person.ifDie() && !person.equals(bullet.getFromPerson()))    //如果击中AI
                                {
                                    flag=true;
                                    bullet.setVisible(false);                   //击中目标后子弹消失
                                    int healthPoint =person.getHealthPoint();
                                    if(healthPoint-bullet.getDamageValue()<=0)
                                    {
                                        bullet.getFromPerson().addKillNum(1);       //这颗子弹的所有者击杀数加1
                                        person.setDie(true);                        //设置人物死亡
                                        person.dieSpecialEffect(gameArea);
                                        /*




                                         */
                                        person.setVisible(false);
                                        createRewardProp(person.getLocation());     //掉落物品
                                    }
                                    else
                                    {
                                        person.reduceHealthPoint(bullet.getDamageValue());
                                    }
                                    deleteBullet[++i]=bullet;       //将击中目标的子弹保存起来
                                    break;
                                }
                            }
                            if(!flag && (ifHitWall(bullet.getLocation(),bullet.getRadius(),true)))    //判断子弹是否撞墙
                            {
                                bullet.setVisible(false);
                                deleteBullet[++i]=bullet ;           //将撞墙的子弹保存起来
                                // MusicPlayer.playBulletHitWallMusic();
                            }
                        }
                        if(i!=-1)
                        {
                            for(int j=0;j<=i;j++)
                            {
                                gameArea.remove(deleteBullet[j]);  //将子弹从gameArea中删除
                                automaticBulletList.remove(deleteBullet[j]);                //将子弹从自动步枪子弹中删除
                            }
                            gameArea.repaint();
                        }
                    }
                }
            });
            automaticBulletThread.start();
            //初始化手榴弹移动线程
            grenadeMoveThread=new Timer(Grenade.speed, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for(Grenade grenade:grenadeList)
                    {
                        //如果手榴弹到达指定地点，或者超出地图范围
                        int x=grenade.getLocation().x;
                        int y=grenade.getLocation().y;
                        if(grenade.ifArrive() || x  > SinglePersonModel.gameAreaWidth ||x<=0 || y> SinglePersonModel.gameAreaHeight || y<=0)
                        {
                            Gson gson=new Gson();
                            String grenadeStr=gson.toJson(grenade);
                            ClientPort.sendStream.println(Sign.GrenadeBoom+grenadeStr);
                        }
                        //否则手雷继续移动
                        else
                        {
                            grenade.next();
                        }
                    }
                }
            });
            grenadeMoveThread.start();
        }
        public void paintComponent(Graphics g)
        {
            URL url=startGame.class.getResource("/images/background.png");
            ImageIcon icon=new ImageIcon(url);
            g.drawImage(icon.getImage(),0,0, 1200, 950,null);
        }
    }

    /**
     * 判断物体是否撞墙，子弹可以穿过河，人不可以，所以判别要区分人和子弹
     * @param point：物体的左上角的坐标
     * @param radius：物体的半径
     * @param isBullet：该物体是子弹的话，则isBullet==true
     * @return
     */
    public boolean ifHitWall(Point point,int radius,boolean isBullet)
    {
        //
        try {
            //先判断物体是否超出地图边界
            if (point.x < 0 || point.y < 0 || point.x + radius > SinglePersonModel.gameAreaWidth || point.y + radius > SinglePersonModel.gameAreaHeight)
                return true;
            //分别判断物体的左上、右上、左下、右下角是与墙重
            if(!isBullet)
            {
                int y = point.x / 20;       //物体在map中的纵坐标
                int x = point.y / 20;       //物体在map中的横坐标
                if (Map.map[x][y] !=0) {
                    return true;
                }
                y = (point.x + 2 * radius) / 20;
                x = point.y / 20;
                if (Map.map[x][y] != 0) {
                    return true;
                }
                y = (point.x / 20);
                x = (point.y + 2 * radius) / 20;
                if (Map.map[x][y] != 0) {
                    return true;
                }
                y = (point.x + 2 * radius) / 20;
                x = (point.y + 2 * radius) / 20;
                if (Map.map[x][y] != 0) {
                    return true;
                }
            }
            else
            {
                int y = point.x / 20;       //物体在map中的纵坐标
                int x = point.y / 20;       //物体在map中的横坐标
                if (Map.map[x][y] ==1) {
                    return true;
                }
                y = (point.x + 2 * radius) / 20;
                x = point.y / 20;
                if (Map.map[x][y] ==1) {
                    return true;
                }
                y = (point.x / 20);
                x = (point.y + 2 * radius) / 20;
                if (Map.map[x][y] ==1) {
                    return true;
                }
                y = (point.x + 2 * radius) / 20;
                x = (point.y + 2 * radius) / 20;
                if (Map.map[x][y] ==1) {
                    return true;
                }
            }
            return false;
        }
        catch (Exception ex)
        {
            return true;
        }
    }

    /**
     * 创建子弹
     * @param fromPerson：射出这个子弹的人
     * @param start：子弹移动的初始坐标
     * @param end：与start构建成一次函数关系
     * @param bulletType：子弹种类
     * @param damageValue：子弹伤害大小
     * @param gunType：射出该子弹的武器类型
     */
    public void createBullet(Person fromPerson,Point start,Point end,int bulletType,int damageValue,int gunType)
    {
        int bulletRadius= BulletSize.getBulletRadius(bulletType);        //根据子弹的类型获取子弹的大小
        Bullet bullet=new Bullet(fromPerson,bulletType,bulletRadius,damageValue,TravelSpeed.bulletTravelSpeed,start,end);
        bullet.setSize(bulletRadius,bulletRadius);
        URL url=startGame.class.getResource("/images/bullet/Bullet.png");
        ImageIcon icon=new ImageIcon(url);
        icon.setImage(icon.getImage().getScaledInstance(bulletRadius,bulletRadius,Image.SCALE_DEFAULT));
        bullet.setIcon(icon);
        switch (gunType)
        {
            case WeaponType.automaticRifle:
                automaticBulletList.add(bullet);
                break;
            case WeaponType.sniperRifle:
                sniperBulletList.add(bullet);
                break;
        }
        gameArea.add(bullet);
    }

    /**
     * 创建掉落在地上的奖励道具
     * @param point：道具在地图上显示的位置
     */
    public void createRewardProp(Point point)
    {
        //进行一次抽签，看脸掉不掉道具
        int result=random.nextInt(100);
        if(result%10==0)
        {
            int type=random.nextInt(RewardType.typeNum);
            RewardProp rewardProp=new RewardProp(type,point);
            rewardPropList.add(rewardProp);
            gameArea.add(rewardProp);
            gameArea.repaint();
        }
    }

    /**
     * person进行攻击
     * @param startPoint：子弹移动的初始位置
     * @param endPoint：
     * @param person
     */
    private void attack(Point startPoint, Point endPoint, Person person)
    {
        Weapon weapon=person.getUsingWeapon();
        //如果是投掷类武器
        if(weapon.getType()==WeaponType.grenade)
        {
            if(!person.ifEmptyGrenade()) {
                Grenade grenade = new Grenade();
                grenade.setFromPerson(person);              //将手雷的所有者设置为person
                grenade.throwGrenade(startPoint, endPoint);
                gameArea.add(grenade);
                grenadeList.add(grenade);
                person.reduceGrenadeNum(1);
                int bulletLeftOnPerson= me.getBulletLeftOnPerson();
                bulletLeft.setText("手雷："+bulletLeftOnPerson);
            }
            else
            {
                MusicPlayer.playBulletUseOutMusic();
            }
        }
        //如果是地雷
        else if(weapon.getType()==WeaponType.mine)
        {
            if(!person.ifEmptyMine())
            {
                MusicPlayer.playDiscontinueAttackMusic(weapon.getWeaponName());
                stepMine(me.getLocation(), person);
                int bulletLeftOnPerson= me.getBulletLeftOnPerson();
                bulletLeft.setText("地雷："+bulletLeftOnPerson);
            }
            else
            {
                MusicPlayer.playBulletUseOutMusic();
            }
        }
        //如果是狙击步枪
        else if(weapon.getType()==WeaponType.sniperRifle)
        {
            SniperRifle gun=(SniperRifle) weapon;
            //判断狙击枪是否在上膛或拉栓状态，如果不是则可以开枪
            if(!me.ifReloading() && !gun.isPollBolt())
            {
                if (!gun.emptyBulletNum() ) //如果还有子弹
                {
                    gun.setPollBolt(true);      //狙击枪进入拉栓状态
                    createBullet(me,startPoint, endPoint, BulletType.k127, weapon.getDamageValue(), weapon.getType());
                    gun.reduceBulletNum(1);     //子弹里面的弹夹减1
                    //修改子弹的数目，在屏幕左上角的显示
                    int bulletLeftInGun=((Gun)weapon).getBulletLeft();
                    int bulletLeftOnPerson= me.getBulletLeftOnPerson();
                    bulletLeft.setText("子弹："+bulletLeftInGun+"/"+bulletLeftOnPerson);

                    MusicPlayer.playDiscontinueAttackMusic(weapon.getWeaponName());
                } else                    //没有子弹
                {
                    MusicPlayer.playBulletUseOutMusic();        //播放没有子弹的声音
                    shotThread.stop();
                }
            }
        }
        //如果是自动步枪
        else if(weapon.getType()==WeaponType.automaticRifle)
        {
            AutomaticRifle gun=(AutomaticRifle) weapon;
            if(!me.ifReloading() )
            {
                if (!gun.emptyBulletNum() ) //如果还有子弹
                {
                    createBullet(me,startPoint, endPoint, ((Gun) weapon).getBulletType(), weapon.getDamageValue(), weapon.getType());
                    gun.reduceBulletNum(1);     //子弹里面的弹夹减1
                    int bulletLeftInGun=((Gun)weapon).getBulletLeft();
                    int bulletLeftOnPerson= me.getBulletLeftOnPerson();
                    bulletLeft.setText("子弹："+bulletLeftInGun+"/"+bulletLeftOnPerson);
                    if(!person.isAttacking())
                    {
                        MusicPlayer.playContinueAttackMusic(gun.getWeaponName());
                        person.setAttacking(true);
                    }
                } else                    //没有子弹
                {
                    if(person.isAttacking())
                    {
                        MusicPlayer.stopContinueAttackMusic();
                    }
                    MusicPlayer.playBulletUseOutMusic();        //播放没有子弹的声音
                    shotThread.stop();
                }
            }
        }
    }
    //安装地雷
    private void stepMine(Point point,Person fromPerson)
    {
        fromPerson.reduceMineNum(1);
        Mine mine=new Mine();
        mine.setFromPerson(fromPerson);
        mine.setLocation(point);
        URL url= SinglePersonModel.class.getResource("/images/Weapon/BoomWeapon/Mine.png");
        ImageIcon icon=new ImageIcon(url);
        icon.setImage(icon.getImage().getScaledInstance(20,20,Image.SCALE_DEFAULT));
        mine.setSize(20,20);
        mine.setIcon(icon);
        mineList.add(mine);
        gameArea.add(mine);
    }


    //是否踩到地雷
    private boolean ifStepMine(Mine mine,Person person)
    {
        Point minePoint=getCentralPoint(mine.getLocation());
        Point eliteSoldierPoint=getCentralPoint(person.getLocation());
        if(minePoint.distance(eliteSoldierPoint) < mine.getRadius() && !person.equals(mine.getFromPerson()))
        {
            return true;
        }
        return false;
    }
    //随机生成一个坐标
    private Point randomPoint()
    {
        int x=random.nextInt(SinglePersonModel.gameAreaWidth /CELL)*CELL;
        int y=random.nextInt(SinglePersonModel.gameAreaHeight/CELL)*CELL;
        return new Point(x,y);
    }
    //获取人物的中心坐标
    public static Point getCentralPoint(Point topLeftCornerPoint)
    {
        return new Point(topLeftCornerPoint.x+CELL/2,topLeftCornerPoint.y+CELL/2);
    }
    private boolean ifHitPerson(Bullet bullet,Person person)
    {
        Point bulletCentralPoint=getCentralPoint(bullet.getLocation());
        Point personCentralPoint=getCentralPoint(person.getLocation());
        if(bulletCentralPoint.distance(personCentralPoint) < bullet.getRadius()+person.getRadius())
            return true;
        return false;
    }
    public static Player getMe(){return me;}
    public static java.util.List getAutomaticBulletList()
    {
        return automaticBulletList;
    }
    public static java.util.List getSniperBulletList()
    {
        return sniperBulletList;
    }
    /**
     * 用于依据标志符号切割掉客户端发送来的字符串开始的命令
     * @param line 每次客户端发送过来的字符串
     * @param cmd 字符串中开头包含的命令
     * @return 返回命令后的字符串
     */
    public static String getRealMessage(String line,String cmd)
    {
        String realMessage=line.substring(cmd.length(),line.length());
        return realMessage;
    }
}
