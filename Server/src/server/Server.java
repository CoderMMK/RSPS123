package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;

import server.clip.region.ObjectDef;
import server.clip.region.Region;
import server.event.CycleEventHandler;
import server.event.Task;
import server.event.TaskScheduler;
import server.model.minigames.FightCaves;
import server.model.minigames.FightPits;
import server.model.minigames.PestControl;
import server.model.npcs.NPCHandler;
import server.model.objects.Doors;
import server.model.objects.DoubleDoors;
import server.model.players.Highscores;
import server.model.players.PlayerHandler;
import server.net.PipelineFactory;
import server.util.MadTurnipConnection;
import server.util.log.Logger;
import server.world.ClanManager;
import server.world.ItemHandler;
import server.world.ObjectHandler;
import server.world.ObjectManager;
import server.world.PlayerManager;
import server.world.ShopHandler;
import server.world.StillGraphicsManager;
import server.world.WalkingCheck;

/**
 * The main class needed to start the server.
 * 
 * @author Sanity
 * @author Graham
 * @author Blake
 * @author Ryan Lmctruck30 Revised by Shawn Notes by Shawn
 */
public class Server {
	
	/**
	 * ClanChat
	 * Added by Valiant
	 */
	public static ClanManager clanManager = new ClanManager();

	/**
	 * Calls to manage the players on the server.
	 */
	public static PlayerManager playerManager = null;
	private static StillGraphicsManager stillGraphicsManager = null;

	/**
	 * Sleep mode of the server.
	 */
	public static boolean sleeping;

	/**
	 * Calls the rate in which an event cycles.
	 */
	public static final int cycleRate;

	/**
	 * Server updating.
	 */
	public static boolean UpdateServer = false;

	/**
	 * Calls in which the server was last saved.
	 */
	public static long lastMassSave = System.currentTimeMillis();

	/**
	 * Calls the usage of CycledEvents.
	 */
	private static long cycleTime, cycles, totalCycleTime, sleepTime;

	/**
	 * Used for debugging the server.
	 */
	private static DecimalFormat debugPercentFormat;

	/**
	 * Forced shutdowns.
	 */
	public static boolean shutdownServer = false;
	public static boolean shutdownClientHandler;

	public static boolean canLoadObjects = false;
	
	/**
	 * Used to identify the server port.
	 */
	public static int serverlistenerPort;

	/**
	 * Calls the usage of player items.
	 */
	public static ItemHandler itemHandler = new ItemHandler();

	/**
	 * Handles logged in players.
	 */
	public static PlayerHandler playerHandler = new PlayerHandler();

	/**
	 * Handles global NPCs.
	 */
	public static NPCHandler npcHandler = new NPCHandler();

	/**
	 * Handles global shops.
	 */
	public static ShopHandler shopHandler = new ShopHandler();

	/**
	 * Handles global objects.
	 */
	public static ObjectHandler objectHandler = new ObjectHandler();
	public static ObjectManager objectManager = new ObjectManager();

	/**
	 * Handles the fightpits minigame.
	 */
	public static FightPits fightPits = new FightPits();

	/**
	 * Handles the pestcontrol minigame.
	 */
	public static PestControl pestControl = new PestControl();

	/**
	 * Handles the fightcaves minigames.
	 */
	public static FightCaves fightCaves = new FightCaves();

	/**
	 * Handles the task scheduler.
	 */
	private static final TaskScheduler scheduler = new TaskScheduler();

	/**
	 * Donate System
	 */
	public static MadTurnipConnection md;

	/**
	 * Gets the task scheduler.
	 */
	public static TaskScheduler getTaskScheduler() {
		return scheduler;
	}

	static {
		if (!Config.SERVER_DEBUG) {
			serverlistenerPort = 43594;
		} else {
			serverlistenerPort = 43594;
		}
		cycleRate = 600;
		shutdownServer = false;
		sleepTime = 0;
		debugPercentFormat = new DecimalFormat("0.0#%");
	}

	/**
	 * Starts the server.
	 */
	 
	public static void main(java.lang.String args[])
			throws NullPointerException, IOException {

		long startTime = System.currentTimeMillis();
		System.setOut(new Logger(System.out));
		System.setErr(new Logger(System.err));
		ObjectDef.loadConfig();
		Region.load();
		WalkingCheck.load();
		// md = new MadTurnipConnection();
		// md.start();
		Highscores.process();
		if (Highscores.connected) {
			System.out
					.println("Ardi Rizal: Highscores has been connect sucessfuly.");
		} else {
			System.out
					.println("Ardi Rizal: Highscores failed connecting database.");
		}
		System.out.println("Lauching: Ardi V1.0");
		System.out.println("NPC Drops Loaded");
		System.out.println("NPC Spawns Loaded");
		System.out.println("Shops Loaded");
		System.out.println("Object Spawns Loaded");
		
		bind();

		playerManager = PlayerManager.getSingleton();
		playerManager.setupRegionPlayers();
		stillGraphicsManager = new StillGraphicsManager();

		Doors.getSingleton().load();
		DoubleDoors.getSingleton().load();
		Connection.initialize();

		long endTime = System.currentTimeMillis();
		long elapsed = endTime - startTime;
		System.out.println("Server started up in " + elapsed + " ms");
		System.out.println("Server listening on port 127.0.0.1:"
				+ serverlistenerPort);

		scheduler.schedule(new Task() {
			@Override
			protected void execute() {
				itemHandler.process();
				playerHandler.process();
				npcHandler.process();
				shopHandler.process();
				CycleEventHandler.getSingleton().process();
				objectManager.process();
				fightPits.process();
				pestControl.process();
			}
		});

	}

	/**
	 * Logging execution.
	 */
	public static boolean playerExecuted = false;

	/**
	 * Gets the sleep mode timer and puts the server into sleep mode.
	 */
	public static long getSleepTimer() {
		return sleepTime;
	}

	/**
	 * Gets the Graphics manager.
	 */
	public static StillGraphicsManager getStillGraphicsManager() {
		return stillGraphicsManager;
	}

	/**
	 * Gets the Player manager.
	 */
	public static PlayerManager getPlayerManager() {
		return playerManager;
	}

	/**
	 * Gets the Object manager.
	 */
	public static ObjectManager getObjectManager() {
		return objectManager;
	}

	/**
	 * Java connection. Ports.
	 */
	private static void bind() {
		ServerBootstrap serverBootstrap = new ServerBootstrap(
				new NioServerSocketChannelFactory(
						Executors.newCachedThreadPool(),
						Executors.newCachedThreadPool()));
		serverBootstrap.setPipelineFactory(new PipelineFactory(
				new HashedWheelTimer()));
		serverBootstrap.bind(new InetSocketAddress(serverlistenerPort));
	}

}
