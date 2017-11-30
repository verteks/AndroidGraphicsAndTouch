package course.labs.graphicslab;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BubbleActivity extends Activity {

	// Эти переменные нужны для тестирования, не изменять
	private final static int RANDOM = 0;
	private final static int SINGLE = 1;
	private final static int STILL = 2;
	private static int speedMode = RANDOM;

	private static final String TAG = "Lab-Graphics";

	// Главный view
	private RelativeLayout mFrame;

	// Bitmap изображения пузыря
	private Bitmap mBitmap;

	// размеры экрана
	private int mDisplayWidth, mDisplayHeight;

	// Звуковые переменные

	// AudioManager
	private AudioManager mAudioManager;
	// SoundPool
	private SoundPool mSoundPool;
	// ID звука лопания пузыря
	private int mSoundID;
	// Громкость аудио
	private float mStreamVolume;

	// Детектор жестов
	private GestureDetector mGestureDetector;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		// Установка пользовательского интерфейса
		mFrame = (RelativeLayout) findViewById(R.id.frame);

		// Загружаем базовое изображение для пузыря
		mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.b64);

	}

	@Override
	protected void onResume() {
		super.onResume();

		// Управляем звуком лопания пузыря
		// Используем AudioManager.STREAM_MUSIC в качестве типа потока

		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

		mStreamVolume = (float) mAudioManager
				.getStreamVolume(AudioManager.STREAM_MUSIC)
				/ mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		// TODO - создаем новый SoundPool, предоставляющий до 10 потоков
		mSoundPool = new SoundPool(10,AudioManager.STREAM_MUSIC,1);
		mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				setupGestureDetector();
				Log.d("Sound Load:","sound loaded successfully");
			}
		});
		mSoundID=mSoundPool.load(BubbleActivity.this,R.raw.bubble_pop,1);


		// TODO - устанавливаем для SoundPool листенер OnLoadCompletedListener который вызывает setupGestureDetector()



		// TODO - загружаем звук из res/raw/bubble_pop.wav

	}


	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {

			// Получаем размер экрана для того чтобы View знал, где границы отображения
			mDisplayWidth = mFrame.getWidth();
			mDisplayHeight = mFrame.getHeight();

		}
	}

	// Устанавливаем GestureDetector
	private void setupGestureDetector() {

		mGestureDetector = new GestureDetector(this,

		new GestureDetector.SimpleOnGestureListener() {

			// Если на BubleView происходит жест швыряния, тогда изменяем его направление и скорость (velocity)

			@Override
			public boolean onFling(MotionEvent event1, MotionEvent event2,
					float velocityX, float velocityY) {

				// TODO - Реализуйте onFling.
				// Вы можете получить все Views в mFrame используя
				// метод ViewGroup.getChildCount()
				for (int i =0;i<mFrame.getChildCount();i++){

					BubbleView bubble = (BubbleView) mFrame.getChildAt(i);
					if (bubble.intersects(event1.getRawX(),event1.getRawY())){
						bubble.deflect(velocityX,velocityY);
						return true;
					}

				}





				return false;

			}

			// Если простое нажатие попадает по BubbleView, тогда лопаем BubbleView
			// Иначе, создаем новый BubbleView в центре нажатия и добавляем его в
			// mFrame. Вы можете получить все компоненты в mFrame с помощью метода ViewGroup.getChildAt()

			@Override
			public boolean onSingleTapConfirmed(MotionEvent event) {

				// TODO - Реализуйте onSingleTapConfirmed.
				// Вы можете получить все объекты View в mFrame используя
				// метод ViewGroup.getChildCount()
				for (int i =0;i<mFrame.getChildCount();i++){

					BubbleView bubble = (BubbleView) mFrame.getChildAt(i);
					if (bubble.intersects(event.getRawX(),event.getRawY())){
						bubble.stop(true);
						return true;
					}

				}

				BubbleView bubbleView = new BubbleView(mFrame.getContext(),event.getRawX(),event.getRawY());
				mFrame.addView(bubbleView);
				bubbleView.start();
				return true;










			}
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// TODO - Делегируем нажатие детектору жестов gestureDetector
		mGestureDetector.onTouchEvent(event);







		return false;

	}

	@Override
	protected void onPause() {

		// TODO - Освобождаем все ресурсы пула SoundPool


		mSoundPool.release();


		super.onPause();
	}

	// BubbleView это View который отображает пузырь.
	// Этот класс управляет анимацией, отрисовкой и лопанием.
	// Новый BubbleView создается отдельно для каждого пузыря на экране

	public class BubbleView extends View {

		private static final int BITMAP_SIZE = 64;
		private static final int REFRESH_RATE = 40;
		private final Paint mPainter = new Paint();
		private ScheduledFuture<?> mMoverFuture;
		private int mScaledBitmapWidth;
		private Bitmap mScaledBitmap;

		// местоположение, скорость и направление пузыря
		private float mXPos, mYPos, mDx, mDy, mRadius, mRadiusSquared;
		private long mRotate, mDRotate;

		BubbleView(Context context, float x, float y) {
			super(context);

			// Создае новое генератор случайных чисел для рандомизации
			// размера, вращения, скорости и направления
			Random r = new Random();

			// Создает битмэп изображения пузыря для этого BubbleView
			createScaledBitmap(r);

			// Радиус Bitmap
			mRadius = mScaledBitmapWidth / 2;
			mRadiusSquared = mRadius * mRadius;

			// Центрируем положение пузыря относительно точки касания пальца пользователя
			mXPos = x - mRadius;
			mYPos = y - mRadius;

			// Устанавливаем скорость и направление BubbleView
			setSpeedAndDirection(r);

			// Устанавливаем вращение BubbleView
			setRotation(r);

			mPainter.setAntiAlias(true);

		}

		private void setRotation(Random r) {

			if (speedMode == RANDOM) {

				// TODO - установить вращение в диапазоне [1..3]
				mDRotate = r.nextInt(3)+1;


			} else {
				mDRotate = 0;

			}
		}

		private void setSpeedAndDirection(Random r) {

			// Используется тестами
			switch (speedMode) {

			case SINGLE:

				mDx = 20;
				mDy = 20;
				break;

			case STILL:

				// Нулевая скорость
				mDx = 0;
				mDy = 0;
				break;

			default:

				// TODO - Устанавливаем направление движения и скорость
				// Ограничиваем скорость движения по x и y
				// в диапазоне [-3..3] пикселя на движение.

				mDx = r.nextFloat() * 6 - 3;
				mDy = r.nextFloat() * 6 - 3;








			}
		}

		private void createScaledBitmap(Random r) {

			if (speedMode != RANDOM) {
				mScaledBitmapWidth = BITMAP_SIZE * 3;

			} else {
				//TODO - устанавливаем масштабирование размера изображения в диапазоне [1..3] * BITMAP_SIZE
				mScaledBitmapWidth = (r.nextInt(3)+1)*BITMAP_SIZE;

			}

			// TODO - Создаем масштабированное изображение, используя размеры, установленные выше.
			mScaledBitmap = Bitmap.createScaledBitmap(mBitmap,mScaledBitmapWidth,mScaledBitmapWidth,true);
		}

		// Начинаем перемещать BubbleView & обновлять экран
		private void start() {

			// Создаем WorkerThread
			ScheduledExecutorService executor = Executors
					.newScheduledThreadPool(1);

			// Запускаем run() в Worker Thread каждые REFRESH_RATE милисекунд
			// Сохраняем ссылку на данный процесс в mMoverFuture
			mMoverFuture = executor.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {

					// TODO - реализуйте логику движения.
					// Каждый раз, когда данный метод запускается, BubbleView должен
					// сдвинуться на один шаг. Если BubbleView покидает экран,
					// останавливаем Рабочий Поток для BubbleView.
					// В противном случае запрашиваем перерисовку BubbleView.
					if (moveWhileOnScreen()){
						stop(true);
					}

					for (int i =0;i<mFrame.getChildCount();i++){
							BubbleView bubble = (BubbleView) mFrame.getChildAt(i);
							if (BubbleView.this != bubble) {
								double center1X = mXPos + (mScaledBitmapWidth / 2);
								double center1Y = mYPos + (mScaledBitmapWidth / 2);
								double center2X = bubble.mXPos + (bubble.mScaledBitmapWidth / 2);
								double center2Y = bubble.mYPos + (bubble.mScaledBitmapWidth / 2);
								double xx = Math.abs(center1X-center2X);
								double yy = Math.abs(center1Y-center2Y);
								double C = Math.pow(Math.pow(xx,2)+Math.pow(yy,2),0.5);
								if (C<=mScaledBitmapWidth/2+bubble.mScaledBitmapWidth/2){
									bubble.stop(true);
									stop(true);
								}
							}

					}




					BubbleView.this.postInvalidate();






				}
			}, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);
		}

		// Возвращает истину, если BubbleView пересекает точку (x,y)
		private synchronized boolean intersects(float x, float y) {

			// TODO - Вернуть true если BubbleView пересекает точку (x,y)
			if ((x<=mXPos+mScaledBitmapWidth)&(x>=mXPos)&(y<=mYPos+mScaledBitmapWidth)&(y>=mYPos)){
				return true;
			}






		    return false;
		}

		// Отменяем движение Пузыря
		// Удаляем Пузырь с mFrame
		// Играем звук лопания, если BubbleView лопнули

		private void stop(final boolean wasPopped) {

			if (null != mMoverFuture && !mMoverFuture.isDone()) {
				mMoverFuture.cancel(true);
			}

			// Данный код будет выполнен в UI потоке
			mFrame.post(new Runnable() {
				@Override
				public void run() {

					// TODO - Удаляем BubbleView из mFrame
					mFrame.removeView(BubbleView.this);


					// TODO - Если пузырь лопнут пользователем,
					// играем звук лопания
					if (wasPopped) {
						mSoundPool.play(mSoundID, 0.5f, 0.5f, 0, 0, 1.0f);



					}
				}
			});
		}

		// Изменяем скорость и направление Пузыря.
		private synchronized void deflect(float velocityX, float velocityY) {

			//TODO - установить mDx и mDy в качестве новых значений velocity, разделив их на REFRESH_RATE
			mDx = velocityX/REFRESH_RATE;
			mDy = velocityY/REFRESH_RATE;






		}

		// Рисуем Пузырь в его текущем положении
		@Override
		protected synchronized void onDraw(Canvas canvas) {

			// TODO - сохраняем canvas
			canvas.save();


			// TODO - Увеличиваем вращение исходного изображения на mDRotate
			mRotate +=mDRotate;



			// TODO Вращаем canvas на текущий сдвиг
			// Подсказка - Вращаем относительно ценра пузыря, а не его положения.
			canvas.rotate((float) mRotate, mXPos + (mScaledBitmapWidth / 2), mYPos + (mScaledBitmapWidth / 2));



			// TODO - рисуем изображение в новом положении

			canvas.drawBitmap(mScaledBitmap, mXPos, mYPos, mPainter);

			// TODO - восстанавливаем canvas

			canvas.restore();

		}

		// Возвращает true если BubbleView все еще на экране после хода
		// operation
		private synchronized boolean moveWhileOnScreen() {

			// TODO - Перемещаем BubbleView
			mXPos+=mDx;
			mYPos+=mDy;


			return isOutOfView();
		}

		// Возвращаем true, если BubbleView ушел с экрана после завершения хода
		private boolean isOutOfView() {

			// TODO - Возвращаем true, если BubbleView вне экрана после завершения хода
			//if (mXPos<0 || mYPos<0 || mXPos>mDisplayWidth || mXPos>mDisplayHeight){return true;}
			if(mXPos<0|| mXPos>mDisplayWidth){mDx*=-1;}
			if(mYPos<0|| mYPos>mDisplayHeight){mDy*=-1;}

			return false;
		}
	}

	// Не изменяйте следующий код

	@Override
	public void onBackPressed() {
		openOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_still_mode:
			speedMode = STILL;
			return true;
		case R.id.menu_single_speed:
			speedMode = SINGLE;
			return true;
		case R.id.menu_random_mode:
			speedMode = RANDOM;
			return true;
		case R.id.quit:
			exitRequested();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void exitRequested() {
		super.onBackPressed();
	}
}