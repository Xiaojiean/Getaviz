class CreateStepScenes < ActiveRecord::Migration[4.2][5.0]
  def change
    create_table :step_scenes do |t|
      t.references :step
      t.references :scene
      t.timestamps
    end
  end
end
